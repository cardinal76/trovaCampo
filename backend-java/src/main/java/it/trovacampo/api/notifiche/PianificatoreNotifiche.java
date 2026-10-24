package it.trovacampo.api.notifiche;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.trovacampo.api.anagrafica.AnagraficaPresenze.Partita;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.notifiche.PartiteDaAvvisare.Avviso;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Chi deve ricevere un avviso adesso, e glielo manda.
 *
 * <p>Gira ogni {@link TipoAvviso#PASSO} (vedi {@link NotificheProgrammate}).
 * A ogni giro chiede a {@link PartiteDaAvvisare} gli avvisi delle partite che
 * cominciano fra poco, e tiene quelli nella loro finestra
 * ({@link TipoAvviso#contiene}):
 *
 * <ul>
 *   <li>{@link TipoAvviso#SQUADRA}: una partita di una squadra seguita, in
 *       casa o fuori, che comincia fra 55 e 65 minuti;
 *   <li>{@link TipoAvviso#VICINO}: una partita che comincia fra 25 e 35
 *       minuti su un campo entro il raggio dalla posizione salvata.
 * </ul>
 *
 * <p>La finestra è larga due passi: ogni partita ci cade in due giri di
 * fila, così un giro saltato (un riavvio, presenze lento) non fa perdere
 * l'avviso. Il doppione del secondo giro lo ferma il {@link RegistroInvii},
 * che sta in Mongo e quindi vale anche dopo un riavvio.
 *
 * <p>Quali partite e a che ora lo dice anche la pagina Prossime partite
 * ({@link ProssimePartite}), con la stessa regola e lo stesso conto.
 */
@Component
public class PianificatoreNotifiche {

    private static final Logger log = LoggerFactory.getLogger(PianificatoreNotifiche.class);

    static final Duration PASSO = TipoAvviso.PASSO;
    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Il JSON che legge il service worker del sito (public/sw.js). */
    record Notifica(String titolo, String testo, String url, String tag) {}

    /** Com'è andato un giro: per il log e per i test. */
    public record Resoconto(int mandate, int scadute, int errori) {}

    private final IscrizioniRepository iscrizioni;
    private final PartiteDaAvvisare daAvvisare;
    private final RegistroInvii registro;
    private final InvioWebPush invio;
    private final ObjectMapper json;
    private final Clock orologio;

    @Autowired
    public PianificatoreNotifiche(
            IscrizioniRepository iscrizioni,
            PartiteDaAvvisare daAvvisare,
            RegistroInvii registro,
            InvioWebPush invio,
            ObjectMapper json) {
        this(iscrizioni, daAvvisare, registro, invio, json, Clock.system(ROMA));
    }

    PianificatoreNotifiche(
            IscrizioniRepository iscrizioni,
            PartiteDaAvvisare daAvvisare,
            RegistroInvii registro,
            InvioWebPush invio,
            ObjectMapper json,
            Clock orologio) {
        this.iscrizioni = iscrizioni;
        this.daAvvisare = daAvvisare;
        this.registro = registro;
        this.invio = invio;
        this.json = json;
        this.orologio = orologio;
    }

    public Resoconto giro() {
        Instant adesso = orologio.instant();
        // Prima le iscrizioni: senza nessuno da avvisare non si chiede niente
        // nemmeno a presenze.
        List<Iscrizione> iscritte = iscrizioni.conAvvisiAccesi();
        if (iscritte.isEmpty()) {
            return new Resoconto(0, 0, 0);
        }
        List<Avviso<Iscrizione>> avvisi =
                daAvvisare.fra(
                        adesso.plus(TipoAvviso.VICINO.anticipo.minus(PASSO)),
                        adesso.plus(TipoAvviso.SQUADRA.anticipo.plus(PASSO)),
                        iscritte);

        Giro giro = new Giro();
        for (Avviso<Iscrizione> avviso : avvisi) {
            if (avviso.tipo().contiene(Duration.between(adesso, avviso.inizio()))) {
                manda(giro, avviso);
            }
        }
        Resoconto resoconto = new Resoconto(giro.mandate, giro.tolte.size(), giro.errori);
        if (giro.mandate + giro.tolte.size() + giro.errori > 0) {
            log.info(
                    "Notifiche: {} mandate, {} iscrizioni scadute tolte, {} non riuscite",
                    resoconto.mandate(), resoconto.scadute(), resoconto.errori());
        }
        return resoconto;
    }

    /** Lo stato di un giro: le iscrizioni tolte non ricevono altro, anche se c'erano altre partite. */
    private static final class Giro {
        final Set<String> tolte = new HashSet<>();
        int mandate;
        int errori;
    }

    private void manda(Giro giro, Avviso<Iscrizione> avviso) {
        Iscrizione iscrizione = avviso.destinatario();
        if (giro.tolte.contains(iscrizione.id())) {
            return;
        }
        String chiave = PartiteDaAvvisare.chiaveInvio(iscrizione.id(), avviso.tipo(), avviso.partita());
        if (!registro.prenota(chiave)) {
            return;
        }
        InvioWebPush.Esito esito =
                invio.invia(
                        iscrizione,
                        contenuto(avviso.partita(), avviso.tipo(), avviso.campo(), avviso.distanzaKm()),
                        durata(avviso.tipo()));
        switch (esito) {
            case CONSEGNATA -> giro.mandate++;
            case SCADUTA -> {
                // Il browser ha tolto l'iscrizione (o il sito è stato
                // disinstallato): non riceverà più niente, e si butta.
                iscrizioni.deleteById(iscrizione.id());
                giro.tolte.add(iscrizione.id());
            }
            case ERRORE -> {
                // Al giro dopo, se la partita è ancora nella finestra, riprova.
                registro.annulla(chiave);
                giro.errori++;
            }
        }
    }

    /** Il servizio push la tiene finché ha senso: dopo l'inizio della partita no. */
    private static Duration durata(TipoAvviso tipo) {
        return tipo.anticipo;
    }

    /**
     * "Tra un'ora: LODIGIANI CALCIO 1972 – VIS SEZZE", e sotto "Alle 15:00 ·
     * ECCELLENZA girone B · La Borghesiana "C"". Il tocco apre la scheda del
     * campo, o la ricerca se il campo non è su TrovaCampo.
     */
    String contenuto(Partita partita, TipoAvviso tipo, Societa campo, Double distanza) {
        String quando =
                distanza == null
                        ? tipo.quando
                        : tipo.quando + ", a " + String.format(Locale.ITALIAN, "%.1f", distanza) + " km";
        String titolo = quando + ": " + partita.casa() + " – " + partita.ospite();

        StringBuilder testo =
                new StringBuilder("Alle ").append(ORA.format(partita.dataOra().atZoneSameInstant(ROMA)));
        if (partita.campionato() != null && !partita.campionato().isBlank()) {
            testo.append(" · ").append(partita.campionato());
            if (partita.girone() != null && !partita.girone().isBlank()) {
                testo.append(" girone ").append(partita.girone());
            }
        }
        if (campo != null && campo.getNomeImpianto() != null) {
            testo.append(" · ").append(campo.getNomeImpianto());
        }
        String url = campo != null && campo.getId() != null ? "/societa/" + campo.getId() : "/";
        String tag =
                "partita-" + PartiteDaAvvisare.identificativo(partita) + "-" + tipo.name().toLowerCase(Locale.ROOT);
        try {
            return json.writeValueAsString(new Notifica(titolo, testo.toString(), url, tag));
        } catch (JsonProcessingException eccezione) {
            throw new IllegalStateException(eccezione);
        }
    }
}
