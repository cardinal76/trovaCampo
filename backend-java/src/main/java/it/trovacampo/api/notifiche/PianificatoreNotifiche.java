package it.trovacampo.api.notifiche;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.trovacampo.api.anagrafica.AnagraficaPresenze.Partita;
import it.trovacampo.api.anagrafica.PartiteSuiCampi;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Chi deve ricevere un avviso adesso, e glielo manda.
 *
 * <p>Gira ogni {@link #PASSO} (vedi {@link NotificheProgrammate}). A ogni
 * giro guarda le partite che cominciano fra poco e le confronta con le
 * iscrizioni:
 *
 * <ul>
 *   <li>{@link Tipo#SQUADRA}: una partita di una squadra seguita, in casa o
 *       fuori, che comincia fra 55 e 65 minuti;
 *   <li>{@link Tipo#VICINO}: una partita che comincia fra 25 e 35 minuti su
 *       un campo entro il raggio dalla posizione salvata.
 * </ul>
 *
 * <p>La finestra è larga due passi: ogni partita ci cade in due giri di
 * fila, così un giro saltato (un riavvio, presenze lento) non fa perdere
 * l'avviso. Il doppione del secondo giro lo ferma il {@link RegistroInvii},
 * che sta in Mongo e quindi vale anche dopo un riavvio.
 */
@Component
public class PianificatoreNotifiche {

    private static final Logger log = LoggerFactory.getLogger(PianificatoreNotifiche.class);

    static final Duration PASSO = Duration.ofMinutes(5);
    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final double RAGGIO_TERRA_KM = 6371.0;

    /** I due avvisi, con l'anticipo e per quanto il servizio push li tiene. */
    enum Tipo {
        SQUADRA(Duration.ofMinutes(60), "Tra un'ora"),
        VICINO(Duration.ofMinutes(30), "Tra mezz'ora");

        final Duration anticipo;
        final String quando;

        Tipo(Duration anticipo, String quando) {
            this.anticipo = anticipo;
            this.quando = quando;
        }

        /** [anticipo - passo, anticipo + passo): mezza finestra prima e mezza dopo. */
        boolean contiene(Duration mancano) {
            return mancano.compareTo(anticipo.minus(PASSO)) >= 0 && mancano.compareTo(anticipo.plus(PASSO)) < 0;
        }
    }

    /** Il JSON che legge il service worker del sito (public/sw.js). */
    record Notifica(String titolo, String testo, String url, String tag) {}

    /** Com'è andato un giro: per il log e per i test. */
    public record Resoconto(int mandate, int scadute, int errori) {}

    private final IscrizioniRepository iscrizioni;
    private final PartiteSuiCampi partite;
    private final SocietaRepository campi;
    private final RegistroInvii registro;
    private final InvioWebPush invio;
    private final ObjectMapper json;
    private final Clock orologio;

    @Autowired
    public PianificatoreNotifiche(
            IscrizioniRepository iscrizioni,
            PartiteSuiCampi partite,
            SocietaRepository campi,
            RegistroInvii registro,
            InvioWebPush invio,
            ObjectMapper json) {
        this(iscrizioni, partite, campi, registro, invio, json, Clock.system(ROMA));
    }

    PianificatoreNotifiche(
            IscrizioniRepository iscrizioni,
            PartiteSuiCampi partite,
            SocietaRepository campi,
            RegistroInvii registro,
            InvioWebPush invio,
            ObjectMapper json,
            Clock orologio) {
        this.iscrizioni = iscrizioni;
        this.partite = partite;
        this.campi = campi;
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
        List<Partita> vicine =
                partite.fra(
                        adesso.plus(Tipo.VICINO.anticipo.minus(PASSO)),
                        adesso.plus(Tipo.SQUADRA.anticipo.plus(PASSO)));
        if (vicine.isEmpty()) {
            return new Resoconto(0, 0, 0);
        }
        Map<Long, List<Societa>> campiPerImpianto =
                campi.findByAnagraficaImpiantoIdIn(
                                vicine.stream().map(Partita::impiantoId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.groupingBy(Societa::getAnagraficaImpiantoId));

        Giro giro = new Giro();
        for (Partita partita : vicine) {
            Duration mancano = Duration.between(adesso, partita.dataOra().toInstant());
            Societa campo = campoDi(partita, campiPerImpianto.getOrDefault(partita.impiantoId(), List.of()));
            if (Tipo.SQUADRA.contiene(mancano)) {
                for (Iscrizione iscrizione : iscritte) {
                    if (iscrizione.avvisoSquadre() && segue(iscrizione, partita)) {
                        manda(giro, iscrizione, partita, Tipo.SQUADRA, campo, null);
                    }
                }
            }
            if (Tipo.VICINO.contiene(mancano) && campo != null && campo.getLat() != null && campo.getLng() != null) {
                for (Iscrizione iscrizione : iscritte) {
                    if (!iscrizione.avvisoVicino() || !iscrizione.haPosizione()) {
                        continue;
                    }
                    double distanza =
                            distanzaKm(iscrizione.lat(), iscrizione.lng(), campo.getLat(), campo.getLng());
                    if (distanza <= iscrizione.raggioKm()) {
                        manda(giro, iscrizione, partita, Tipo.VICINO, campo, distanza);
                    }
                }
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

    private void manda(Giro giro, Iscrizione iscrizione, Partita partita, Tipo tipo, Societa campo, Double distanza) {
        if (giro.tolte.contains(iscrizione.id())) {
            return;
        }
        // Con la data dentro: una partita spostata ad altra ora si riannuncia.
        String chiave =
                iscrizione.id() + "|" + tipo + "|" + identificativo(partita) + "|"
                        + partita.dataOra().toInstant().getEpochSecond();
        if (!registro.prenota(chiave)) {
            return;
        }
        InvioWebPush.Esito esito = invio.invia(iscrizione, contenuto(partita, tipo, campo, distanza), durata(tipo));
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
    private static Duration durata(Tipo tipo) {
        return tipo.anticipo;
    }

    private static boolean segue(Iscrizione iscrizione, Partita partita) {
        return ChiaveSquadra.seguita(
                        iscrizione.squadre(), partita.casaSocietaId(), partita.campionato(), partita.ente(),
                        partita.casaSquadra())
                || ChiaveSquadra.seguita(
                        iscrizione.squadre(), partita.ospiteSocietaId(), partita.campionato(), partita.ente(),
                        partita.ospiteSquadra());
    }

    /**
     * Il campo di TrovaCampo dove si gioca: fra le società che giocano su
     * quell'impianto, quella di casa se c'è (la notifica apre la sua scheda),
     * altrimenti la prima con le coordinate.
     */
    private static Societa campoDi(Partita partita, List<Societa> sullImpianto) {
        return sullImpianto.stream()
                .min(Comparator.comparing(
                                (Societa societa) -> !Objects.equals(
                                        societa.getAnagraficaSocietaId(), partita.casaSocietaId()))
                        .thenComparing(societa -> societa.getLat() == null)
                        .thenComparing(Societa::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static String identificativo(Partita partita) {
        return partita.id() != null
                ? String.valueOf(partita.id())
                : partita.impiantoId() + "-" + partita.casa() + "-" + partita.ospite();
    }

    /**
     * "Tra un'ora: LODIGIANI CALCIO 1972 – VIS SEZZE", e sotto "Alle 15:00 ·
     * ECCELLENZA girone B · La Borghesiana "C"". Il tocco apre la scheda del
     * campo, o la ricerca se il campo non è su TrovaCampo.
     */
    String contenuto(Partita partita, Tipo tipo, Societa campo, Double distanza) {
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
        String tag = "partita-" + identificativo(partita) + "-" + tipo.name().toLowerCase(Locale.ROOT);
        try {
            return json.writeValueAsString(new Notifica(titolo, testo.toString(), url, tag));
        } catch (JsonProcessingException eccezione) {
            throw new IllegalStateException(eccezione);
        }
    }

    /** Haversine: sulle decine di chilometri l'errore è di metri. */
    static double distanzaKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RAGGIO_TERRA_KM * Math.asin(Math.sqrt(a));
    }
}
