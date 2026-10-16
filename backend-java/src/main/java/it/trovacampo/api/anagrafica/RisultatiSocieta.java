package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * L'ultima partita giocata da ogni squadra di una società, dal calendario di
 * presenze.
 *
 * <p><b>Una riga per squadra, non le ultime N partite.</b> Prima si chiedevano
 * le ultime dieci, e quel taglio mescolava le categorie: dieci risultati
 * potevano essere quattro dell'Under 17 e nessuno della prima squadra, perché
 * un girone gioca il sabato e un altro la domenica. Chi apre questa scheda
 * chiede «come sono andate le squadre di questa società», e le vuole tutte.
 *
 * <p>La scheda non dice soltanto quali sono i risultati, ma anche perché non
 * ce ne sono: a inizio stagione la società non ha ancora giocato, presenze
 * può non avere l'endpoint (TrovaCampo si rilascia per conto suo, e la scheda
 * può arrivare sul server prima), e presenze può essere giù. Tre motivi
 * diversi, tre frasi diverse per chi guarda: perciò non si manda solo un
 * elenco, ma lo {@link Stato} con cui l'elenco va letto.
 *
 * <p>Niente cache, al contrario di {@link PartiteSuiCampi}: la scheda chiede
 * i risultati di una società sola, e solo quando qualcuno apre quella
 * sezione. Una chiamata per apertura, non una per visita.
 */
@Service
public class RisultatiSocieta {

    private static final Logger log = LoggerFactory.getLogger(RisultatiSocieta.class);

    /** Come va letto l'elenco dei risultati. */
    public enum Stato {
        /** Presenze ha risposto: l'elenco è quello che ha, anche se vuoto. */
        DISPONIBILI,
        /**
         * Presenze non ha i risultati di questa società: non conosce la
         * società, o è un presenze senza l'endpoint (404). Riprovare non
         * serve, e la scheda non promette di riprovare.
         */
        NON_DISPONIBILI,
        /** Presenze non ha risposto: un guasto o un timeout, e fra poco può andare. */
        ERRORE
    }

    /**
     * @param risultati una riga per squadra -- l'ultima che ognuna ha giocato
     *     -- dalla più recente; vuoto se la società non ha ancora giocato
     */
    public record Risultati(Stato stato, List<Risultato> risultati) {}

    /**
     * Un risultato come lo mostra la scheda.
     *
     * @param data con il fuso di Roma, come la scrive il comunicato; nulla se
     *     presenze non sa l'ora della partita
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali
     * @param campionatoId il campionato in presenze, per il collegamento alla sua
     *     pagina pubblica; nullo da un presenze che non lo manda ancora
     * @param gironeId il girone in presenze: e' la pagina con la classifica e il
     *     calendario, cioe' quella che si cerca davvero leggendo «Girone B»
     * @param girone vuoto finché i gironi non escono
     * @param squadra vuota per la prima squadra, "B" per la seconda
     * @param squadraIscrittaId la squadra della riga: è la chiave con cui
     *     l'elenco si disegna, perché nel derby fra la prima e la squadra B la
     *     stessa partita arriva due volte, una per squadra
     * @param inCasa da che parte giocava la squadra della riga
     * @param esito VITTORIA, PAREGGIO o SCONFITTA dal suo punto di vista: lo
     *     decide presenze e passa così com'è, senza riconfrontare i gol
     */
    public record Risultato(
            Long squadraIscrittaId,
            OffsetDateTime data,
            Long campionatoId,
            String campionato,
            String ente,
            Long gironeId,
            String girone,
            Integer giornata,
            String squadra,
            String casa,
            String ospite,
            Integer golCasa,
            Integer golOspite,
            boolean inCasa,
            String esito) {}

    private final AnagraficaPresenze anagrafica;
    private final SocietaInPresenze inPresenze;

    public RisultatiSocieta(AnagraficaPresenze anagrafica, SocietaInPresenze inPresenze) {
        this.anagrafica = anagrafica;
        this.inPresenze = inPresenze;
    }

    public Risultati di(Societa societa) {
        Optional<Long> id = inPresenze.idDi(societa);
        if (id.isEmpty()) {
            // Un campo inserito a mano, o con un nome che presenze non
            // riconosce: non ci sono risultati da chiedere per nessuno.
            return new Risultati(Stato.NON_DISPONIBILI, List.of());
        }
        try {
            AnagraficaPresenze.Risultati letti = anagrafica.risultati(id.get());
            if (letti == null || letti.risultati() == null) {
                return new Risultati(Stato.DISPONIBILI, List.of());
            }
            // L'ordine è quello di presenze, dal più recente: non si riordina,
            // perché una partita senza data non saprebbe dove stare.
            return new Risultati(
                    Stato.DISPONIBILI, letti.risultati().stream().map(RisultatiSocieta::perChiGuarda).toList());
        } catch (HttpClientErrorException.NotFound mancante) {
            log.debug("Presenze non ha i risultati della societa {}: {}", id.get(), mancante.getMessage());
            return new Risultati(Stato.NON_DISPONIBILI, List.of());
        } catch (RuntimeException guasto) {
            log.warn("Risultati di presenze non disponibili: {}", guasto.toString());
            return new Risultati(Stato.ERRORE, List.of());
        }
    }

    private static Risultato perChiGuarda(AnagraficaPresenze.Risultato risultato) {
        return new Risultato(
                risultato.squadraIscrittaId(),
                risultato.data() == null
                        ? null
                        : risultato.data().atZoneSameInstant(PartiteSuiCampi.ROMA).toOffsetDateTime(),
                risultato.campionatoId(),
                risultato.campionato(),
                risultato.ente(),
                risultato.gironeId(),
                risultato.girone(),
                risultato.giornata(),
                risultato.squadra(),
                risultato.casa(),
                risultato.ospite(),
                risultato.golCasa(),
                risultato.golOspite(),
                risultato.inCasa(),
                risultato.esito());
    }
}
