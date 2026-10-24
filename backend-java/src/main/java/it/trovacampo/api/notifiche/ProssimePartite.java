package it.trovacampo.api.notifiche;

import it.trovacampo.api.anagrafica.AnagraficaPresenze.Partita;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.notifiche.PartiteDaAvvisare.Avviso;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * La pagina Prossime partite: le partite della settimana per cui a questo
 * browser arriverebbe un avviso, con l'ora dell'avviso, o con «inviato» se
 * è già partito.
 *
 * <p>Le partite le sceglie {@link PartiteDaAvvisare}, la stessa regola del
 * {@link PianificatoreNotifiche}, e l'ora la calcola
 * {@link TipoAvviso#previsto}, lo stesso conto della sua finestra: la pagina
 * e le notifiche dicono la stessa cosa. La posizione si arrotonda come quella
 * salvata con l'iscrizione, così anche le distanze sono le stesse.
 *
 * <p>Si risponde solo sulle scelte che il browser manda: niente si salva e
 * niente si legge delle iscrizioni. Il solo legame con un'iscrizione è
 * l'endpoint, se il browser lo manda, per cercare nel {@link RegistroInvii}
 * gli avvisi già partiti verso di lui: l'endpoint lo conosce solo quel
 * browser, e dal registro si sa soltanto se un avviso c'è stato.
 */
@Service
public class ProssimePartite {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    /**
     * Oggi e i sette giorni dopo: una giornata di campionato intera, anche
     * guardando di sabato. La cache delle partite ne copre quattordici.
     */
    static final int GIORNI = 7;
    /** Venti chilometri in città sono tanti campi: oltre, la pagina non serve più. */
    static final int MASSIMO_PARTITE = 100;

    static final String PREVISTO = "previsto";
    static final String INVIATO = "inviato";
    static final String PASSATO = "passato";

    /** Le scelte arrivate dal browser, come le leggerebbe il pianificatore da un'iscrizione. */
    record Scelte(List<String> squadre, Double lat, Double lng, Integer raggioKm) implements Destinatario {

        @Override
        public boolean avvisoSquadre() {
            return !squadre.isEmpty();
        }

        @Override
        public boolean avvisoVicino() {
            return lat != null && lng != null;
        }
    }

    /**
     * La risposta.
     *
     * @param fino l'ultimo giorno guardato, compreso
     * @param altre vero se le partite erano più di {@link #MASSIMO_PARTITE} e
     *     l'elenco si ferma prima
     */
    public record Elenco(LocalDate fino, List<ProssimaPartita> partite, boolean altre) {}

    /**
     * Una partita, con il campo e gli avvisi che la riguardano (uno, o due se
     * è di una squadra seguita e anche vicina).
     */
    public record ProssimaPartita(
            OffsetDateTime dataOra,
            String casa,
            String ospite,
            Long campionatoId,
            String campionato,
            String ente,
            Long gironeId,
            String girone,
            int giornata,
            Campo campo,
            List<AvvisoPrevisto> avvisi) {}

    /** Il campo di TrovaCampo dove si gioca, per il collegamento alla scheda. */
    public record Campo(
            String id, String nomeSocieta, String nomeImpianto, String indirizzo, String localita, String provincia) {}

    /**
     * @param tipo "squadra" o "vicino"
     * @param seguite per "squadra": le squadre della partita che si seguono
     * @param distanzaKm per "vicino", a un decimale
     * @param alle il giro che manda (o ha mandato) l'avviso
     * @param stato "previsto", "inviato" (lo dice il registro, se il browser ha
     *     mandato l'endpoint) o "passato" (l'ora è passata e a questo browser
     *     non risulta arrivato)
     */
    public record AvvisoPrevisto(
            String tipo, List<String> seguite, Double distanzaKm, OffsetDateTime alle, String stato) {}

    private final PartiteDaAvvisare daAvvisare;
    private final RegistroInvii registro;
    private final Clock orologio;

    @Autowired
    public ProssimePartite(PartiteDaAvvisare daAvvisare, RegistroInvii registro) {
        this(daAvvisare, registro, Clock.system(ROMA));
    }

    ProssimePartite(PartiteDaAvvisare daAvvisare, RegistroInvii registro, Clock orologio) {
        this.daAvvisare = daAvvisare;
        this.registro = registro;
        this.orologio = orologio;
    }

    public Elenco elenco(ProssimeRequest richiesta) {
        Instant adesso = orologio.instant();
        LocalDate oggi = LocalDate.ofInstant(adesso, ROMA);
        LocalDate fino = oggi.plusDays(GIORNI);
        Scelte scelte = scelteDi(richiesta);
        if (!scelte.avvisoSquadre() && !scelte.avvisoVicino()) {
            return new Elenco(fino, List.of(), false);
        }

        // Dalle partite che devono ancora cominciare: quelle già iniziate
        // non hanno più un avviso da aspettare.
        List<Avviso<Scelte>> avvisi =
                daAvvisare.fra(adesso, fino.plusDays(1).atStartOfDay(ROMA).toInstant(), List.of(scelte));

        String iscrizione =
                richiesta.endpoint() == null || richiesta.endpoint().isBlank()
                        ? null
                        : Iscrizione.idDi(richiesta.endpoint());
        Set<String> inviati =
                iscrizione == null
                        ? Set.of()
                        : registro.giaPrenotati(
                                avvisi.stream()
                                        .map(avviso -> PartiteDaAvvisare.chiaveInvio(
                                                iscrizione, avviso.tipo(), avviso.partita()))
                                        .toList());

        // Una riga per partita, nell'ordine degli avvisi (cioè per orario).
        Map<String, List<Avviso<Scelte>>> perPartita = new LinkedHashMap<>();
        for (Avviso<Scelte> avviso : avvisi) {
            perPartita
                    .computeIfAbsent(
                            PartiteDaAvvisare.identificativo(avviso.partita()) + "|" + avviso.inizio(),
                            chiave -> new ArrayList<>())
                    .add(avviso);
        }
        List<ProssimaPartita> partite = new ArrayList<>();
        for (List<Avviso<Scelte>> dellaPartita : perPartita.values()) {
            if (partite.size() == MASSIMO_PARTITE) {
                return new Elenco(fino, partite, true);
            }
            partite.add(riga(dellaPartita, adesso, iscrizione, inviati));
        }
        return new Elenco(fino, partite, false);
    }

    /** Come {@link IscrizioniService}: la posizione arrotondata, e solo se serve. */
    private static Scelte scelteDi(ProssimeRequest richiesta) {
        List<String> squadre = richiesta.squadre().stream().distinct().toList();
        if (richiesta.posizione() == null) {
            return new Scelte(squadre, null, null, null);
        }
        return new Scelte(
                squadre,
                IscrizioniService.arrotonda(richiesta.posizione().lat()),
                IscrizioniService.arrotonda(richiesta.posizione().lng()),
                richiesta.raggioKm() == null ? IscrizioniService.RAGGIO_PREDEFINITO_KM : richiesta.raggioKm());
    }

    private static ProssimaPartita riga(
            List<Avviso<Scelte>> avvisi, Instant adesso, String iscrizione, Set<String> inviati) {
        Avviso<Scelte> primo = avvisi.getFirst();
        Partita partita = primo.partita();
        List<AvvisoPrevisto> previsti = new ArrayList<>();
        for (Avviso<Scelte> avviso : avvisi) {
            Instant alle = avviso.tipo().previsto(avviso.inizio());
            String stato;
            if (iscrizione != null
                    && inviati.contains(PartiteDaAvvisare.chiaveInvio(iscrizione, avviso.tipo(), partita))) {
                stato = INVIATO;
            } else if (!adesso.isBefore(alle.plus(TipoAvviso.PASSO))) {
                // Anche il giro di riserva è passato: l'avviso non arriva più.
                stato = PASSATO;
            } else {
                stato = PREVISTO;
            }
            previsti.add(
                    new AvvisoPrevisto(
                            avviso.tipo().name().toLowerCase(Locale.ROOT),
                            avviso.tipo() == TipoAvviso.SQUADRA
                                    ? PartiteDaAvvisare.seguite(avviso.destinatario(), partita)
                                    : List.of(),
                            avviso.distanzaKm() == null ? null : Math.round(avviso.distanzaKm() * 10) / 10.0,
                            alle.atZone(ROMA).toOffsetDateTime(),
                            stato));
        }
        Societa campo = primo.campo();
        return new ProssimaPartita(
                partita.dataOra().atZoneSameInstant(ROMA).toOffsetDateTime(),
                partita.casa(),
                partita.ospite(),
                partita.campionatoId(),
                partita.campionato(),
                partita.ente(),
                partita.gironeId(),
                partita.girone(),
                partita.giornata(),
                campo == null
                        ? null
                        : new Campo(
                                campo.getId(),
                                campo.getNomeSocieta(),
                                campo.getNomeImpianto(),
                                campo.getIndirizzoImpianto(),
                                campo.getLocalitaImpianto(),
                                campo.getProvinciaImpianto()),
                previsti);
    }
}
