package it.trovacampo.api.anagrafica;

import it.trovacampo.api.societa.DatiNonValidiException;
import it.trovacampo.api.societa.SocietaNonTrovataException;
import it.trovacampo.api.societa.SocietaService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le rotte pubbliche che servono dati dell'anagrafica di presenze (squadre,
 * partite, risultati, punteggi in diretta) per le società di TrovaCampo.
 * Stesso contratto REST di sempre: cambia solo il modulo che le serve.
 */
@RestController
@RequestMapping("/api")
public class AnagraficaController {

    private final SocietaService service;
    private final SquadreSocieta squadre;
    private final PartiteSuiCampi partite;
    private final RisultatiSocieta risultati;

    public AnagraficaController(
            SocietaService service,
            SquadreSocieta squadre,
            PartiteSuiCampi partite,
            RisultatiSocieta risultati) {
        this.service = service;
        this.squadre = squadre;
        this.partite = partite;
        this.risultati = risultati;
    }

    /**
     * Per la mappa, in una chiamata sola: le prime partite della settimana
     * di ogni campo, con chiave l'id del campo in presenze
     * ({@code anagraficaImpiantoId} di ogni società). Vuoto, e non un
     * errore, se presenze non risponde.
     */
    @GetMapping("/campi/partite")
    public Map<Long, List<PartiteSuiCampi.Partita>> partiteSuiCampi() {
        return partite.perLaMappa();
    }

    /**
     * I soli punteggi in diretta di queste partite (gli {@code id} di presenze
     * delle partite di scheda e mappa): mentre si gioca la pagina chiede questi
     * e cambia solo quelli. Una partita assente non è in diretta.
     */
    @GetMapping("/partite/live")
    public Map<Long, AnagraficaPresenze.Live> live(@RequestParam List<Long> ids) {
        if (ids.size() > PartiteSuiCampi.MASSIMO_LIVE) {
            throw new DatiNonValidiException(
                    "Al massimo " + PartiteSuiCampi.MASSIMO_LIVE + " partite per richiesta");
        }
        return partite.live(ids);
    }

    /**
     * Le squadre della società con il campionato di ognuna, lette
     * dall'anagrafica di presenze. Vuoto per un campo che non viene da lì.
     */
    @GetMapping("/societa/{id}/squadre")
    public List<SquadreSocieta.Squadra> squadre(@PathVariable String id) {
        return squadre.di(service.perId(id).orElseThrow(SocietaNonTrovataException::new));
    }

    /**
     * Le prossime partite sul campo di questa società, dal calendario di
     * presenze. Vuoto per un campo che non viene da lì, o se presenze non
     * risponde.
     */
    @GetMapping("/societa/{id}/partite")
    public List<PartiteSuiCampi.Partita> partite(@PathVariable String id) {
        return partite.sulCampo(service.perId(id).orElseThrow(SocietaNonTrovataException::new));
    }

    /**
     * Gli ultimi risultati della società, dal calendario di presenze. Risponde
     * sempre 200: lo {@code stato} dice se i risultati ci sono, se presenze non
     * li ha (anche perché non ha ancora l'endpoint) o se non ha risposto, e la
     * scheda lo racconta a parole invece di rompersi.
     */
    @GetMapping("/societa/{id}/risultati")
    public RisultatiSocieta.Risultati risultati(@PathVariable String id) {
        return risultati.di(service.perId(id).orElseThrow(SocietaNonTrovataException::new));
    }
}
