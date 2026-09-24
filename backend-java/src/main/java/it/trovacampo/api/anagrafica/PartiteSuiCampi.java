package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Le prossime partite su ogni campo, dal calendario di presenze.
 *
 * <p>Una partita si abbina a un campo per l'id dell'impianto in presenze
 * ({@link Societa#getAnagraficaImpiantoId()}, messo dalla sincronizzazione),
 * mai per nome. Il campo di una partita è quello di casa di chi ospita:
 * presenze non registra un campo per la singola partita.
 *
 * <p>Le partite di due settimane si chiedono a presenze con una chiamata sola
 * e si tengono per qualche minuto: la mappa ha qualche centinaio di campi e
 * la apre chiunque, una chiamata per campo o per visita sarebbe troppo.
 *
 * <p>Se presenze non risponde non è un errore per chi guarda: scheda e mappa
 * funzionano lo stesso, con le partite dell'ultima lettura riuscita o senza
 * partite. Per un minuto non si riprova, perché ogni tentativo aspetterebbe
 * il timeout.
 */
@Service
public class PartiteSuiCampi {

    private static final Logger log = LoggerFactory.getLogger(PartiteSuiCampi.class);

    /** I giorni del calendario sono quelli italiani. */
    static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    /** La scheda guarda due settimane avanti, oggi compreso. */
    static final int GIORNI_SCHEDA = 14;
    /** La mappa una: una giornata di campionato. */
    static final int GIORNI_MAPPA = 7;
    /** Un campo con tante squadre ha anche cinque o sei partite a weekend. */
    static final int MASSIMO_SCHEDA = 30;
    /** Nel popup ne stanno poche. */
    static final int MASSIMO_MAPPA = 3;
    static final Duration DURATA_CACHE = Duration.ofMinutes(5);
    static final Duration PAUSA_DOPO_ERRORE = Duration.ofMinutes(1);
    /**
     * Una partita cominciata da meno di due ore si sta ancora giocando: chi
     * cerca il campo adesso la vuole vedere.
     */
    static final Duration IN_CORSO = Duration.ofHours(2);

    /**
     * Una partita come la mostrano scheda e mappa.
     *
     * @param dataOra con il fuso di Roma, come la scrive il comunicato
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali
     */
    public record Partita(
            OffsetDateTime dataOra,
            String casa,
            String ospite,
            String campionato,
            String ente,
            String girone,
            int giornata) {}

    private final AnagraficaPresenze anagrafica;
    private final Clock orologio;

    private List<AnagraficaPresenze.Partita> lette = List.of();
    /** Quando si è provato l'ultima volta, riuscito o no; null prima della prima. */
    private Instant ultimoTentativo;
    private boolean ultimoRiuscito;

    @Autowired
    public PartiteSuiCampi(AnagraficaPresenze anagrafica) {
        this(anagrafica, Clock.system(ROMA));
    }

    PartiteSuiCampi(AnagraficaPresenze anagrafica, Clock orologio) {
        this.anagrafica = anagrafica;
        this.orologio = orologio;
    }

    /** Le prossime partite sul campo di questa società, per data. Vuoto se il campo non viene da presenze. */
    public List<Partita> sulCampo(Societa societa) {
        Long impianto = societa.getAnagraficaImpiantoId();
        if (impianto == null) {
            return List.of();
        }
        return prossime(GIORNI_SCHEDA).stream()
                .filter(partita -> impianto.equals(partita.impiantoId()))
                .limit(MASSIMO_SCHEDA)
                .map(PartiteSuiCampi::perChiGuarda)
                .toList();
    }

    /**
     * Per la mappa: per ogni campo di presenze (l'id del suo impianto) le
     * prime partite della settimana. Un campo senza partite non c'è.
     */
    public Map<Long, List<Partita>> perLaMappa() {
        Map<Long, List<Partita>> perCampo = new LinkedHashMap<>();
        for (AnagraficaPresenze.Partita partita : prossime(GIORNI_MAPPA)) {
            List<Partita> sulCampo =
                    perCampo.computeIfAbsent(partita.impiantoId(), id -> new ArrayList<>());
            if (sulCampo.size() < MASSIMO_MAPPA) {
                sulCampo.add(perChiGuarda(partita));
            }
        }
        return perCampo;
    }

    /**
     * Da giocare, con data e campo, dall'ora di adesso (meno quelle in corso)
     * fino alla fine dell'ultimo dei {@code giorni}.
     */
    private List<AnagraficaPresenze.Partita> prossime(int giorni) {
        Instant adesso = orologio.instant();
        Instant da = adesso.minus(IN_CORSO);
        Instant fino = LocalDate.now(orologio).plusDays(giorni).atStartOfDay(ROMA).toInstant();
        return lette().stream()
                .filter(partita -> "DA_GIOCARE".equals(partita.stato()))
                .filter(partita -> partita.dataOra() != null && partita.impiantoId() != null)
                .filter(partita -> !partita.dataOra().toInstant().isBefore(da))
                .filter(partita -> partita.dataOra().toInstant().isBefore(fino))
                .sorted(Comparator.comparing(AnagraficaPresenze.Partita::dataOra))
                .toList();
    }

    /**
     * Le partite delle due settimane, dalla cache se è fresca. Sincronizzato:
     * dieci persone che aprono la mappa insieme fanno una chiamata sola.
     */
    private synchronized List<AnagraficaPresenze.Partita> lette() {
        Instant adesso = orologio.instant();
        Duration validita = ultimoRiuscito ? DURATA_CACHE : PAUSA_DOPO_ERRORE;
        if (ultimoTentativo != null && adesso.isBefore(ultimoTentativo.plus(validita))) {
            return lette;
        }
        ultimoTentativo = adesso;
        LocalDate oggi = LocalDate.now(orologio);
        try {
            lette = anagrafica.partite(oggi, oggi.plusDays(GIORNI_SCHEDA - 1));
            ultimoRiuscito = true;
        } catch (RuntimeException eccezione) {
            // Si tengono quelle dell'ultima lettura: prossime() scarta da sé
            // quelle ormai passate.
            ultimoRiuscito = false;
            log.warn("Partite di presenze non disponibili: {}", eccezione.toString());
        }
        return lette;
    }

    private static Partita perChiGuarda(AnagraficaPresenze.Partita partita) {
        return new Partita(
                partita.dataOra().atZoneSameInstant(ROMA).toOffsetDateTime(),
                partita.casa(),
                partita.ospite(),
                partita.campionato(),
                partita.ente(),
                partita.girone(),
                partita.giornata());
    }
}
