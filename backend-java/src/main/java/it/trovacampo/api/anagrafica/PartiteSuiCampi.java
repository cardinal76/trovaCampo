package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
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
 * <p>Nella scheda ogni squadra ha anche il suo stemma, preso dalle società
 * di TrovaCampo legate alla stessa società di presenze
 * ({@link Societa#getAnagraficaSocietaId()}): presenze non lo manda con le
 * partite. La mappa non li mostra e non li cerca.
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
     * @param casaLogoUrl lo stemma della squadra di casa; nullo sulla mappa e quando
     *     TrovaCampo non ce l'ha
     * @param ospiteLogoUrl lo stesso, per gli ospiti
     */
    public record Partita(
            OffsetDateTime dataOra,
            String casa,
            String ospite,
            String campionato,
            String ente,
            String girone,
            int giornata,
            String casaLogoUrl,
            String ospiteLogoUrl) {}

    private final AnagraficaPresenze anagrafica;
    private final SocietaRepository societa;
    private final Clock orologio;

    private List<AnagraficaPresenze.Partita> lette = List.of();
    /** Quando si è provato l'ultima volta, riuscito o no; null prima della prima. */
    private Instant ultimoTentativo;
    private boolean ultimoRiuscito;

    @Autowired
    public PartiteSuiCampi(AnagraficaPresenze anagrafica, SocietaRepository societa) {
        this(anagrafica, societa, Clock.system(ROMA));
    }

    PartiteSuiCampi(AnagraficaPresenze anagrafica, SocietaRepository societa, Clock orologio) {
        this.anagrafica = anagrafica;
        this.societa = societa;
        this.orologio = orologio;
    }

    /** Le prossime partite sul campo di questa società, per data. Vuoto se il campo non viene da presenze. */
    public List<Partita> sulCampo(Societa societa) {
        Long impianto = societa.getAnagraficaImpiantoId();
        if (impianto == null) {
            return List.of();
        }
        List<AnagraficaPresenze.Partita> sulCampo = prossime(GIORNI_SCHEDA).stream()
                .filter(partita -> impianto.equals(partita.impiantoId()))
                .limit(MASSIMO_SCHEDA)
                .toList();
        Map<Long, String> stemmi = stemmiDi(sulCampo);
        return sulCampo.stream().map(partita -> perChiGuarda(partita, stemmi)).toList();
    }

    /**
     * Gli stemmi delle squadre di queste partite, per id della società in
     * presenze, con una query sola. Una società con più campi ha più righe in
     * Mongo: vale il primo stemma trovato.
     */
    private Map<Long, String> stemmiDi(List<AnagraficaPresenze.Partita> partite) {
        Collection<Long> ids = partite.stream()
                .flatMap(partita -> Stream.of(partita.casaSocietaId(), partita.ospiteSocietaId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> stemmi = new HashMap<>();
        if (ids.isEmpty()) {
            return stemmi;
        }
        for (Societa riga : societa.stemmiDi(ids)) {
            if (riga.getLogoUrl() != null && !riga.getLogoUrl().isBlank()) {
                stemmi.putIfAbsent(riga.getAnagraficaSocietaId(), riga.getLogoUrl());
            }
        }
        return stemmi;
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
                sulCampo.add(perChiGuarda(partita, Map.of()));
            }
        }
        return perCampo;
    }

    /**
     * Per le notifiche: le partite da giocare, con data e campo, che
     * cominciano fra {@code da} (compreso) e {@code a} (escluso), così come
     * arrivano da presenze, con le società e le squadre. Dalla stessa cache
     * di scheda e mappa: il pianificatore non aggiunge chiamate a presenze
     * oltre a una ogni {@link #DURATA_CACHE}.
     */
    public List<AnagraficaPresenze.Partita> fra(Instant da, Instant a) {
        return lette().stream()
                .filter(partita -> "DA_GIOCARE".equals(partita.stato()))
                .filter(partita -> partita.dataOra() != null && partita.impiantoId() != null)
                .filter(partita -> !partita.dataOra().toInstant().isBefore(da))
                .filter(partita -> partita.dataOra().toInstant().isBefore(a))
                .sorted(Comparator.comparing(AnagraficaPresenze.Partita::dataOra))
                .toList();
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

    private static Partita perChiGuarda(AnagraficaPresenze.Partita partita, Map<Long, String> stemmi) {
        return new Partita(
                partita.dataOra().atZoneSameInstant(ROMA).toOffsetDateTime(),
                partita.casa(),
                partita.ospite(),
                partita.campionato(),
                partita.ente(),
                partita.girone(),
                partita.giornata(),
                stemma(stemmi, partita.casaSocietaId()),
                stemma(stemmi, partita.ospiteSocietaId()));
    }

    private static String stemma(Map<Long, String> stemmi, Long societaId) {
        return societaId == null ? null : stemmi.get(societaId);
    }
}
