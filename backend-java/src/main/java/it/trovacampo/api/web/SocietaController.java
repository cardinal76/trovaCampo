package it.trovacampo.api.web;

import it.trovacampo.api.anagrafica.PartiteSuiCampi;
import it.trovacampo.api.anagrafica.SquadreSocieta;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.service.SocietaService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stesso contratto REST dell'API Node (backend/), così i client sono intercambiabili. */
@RestController
@RequestMapping("/api")
public class SocietaController {

    private final SocietaService service;
    private final SquadreSocieta squadre;
    private final PartiteSuiCampi partite;

    public SocietaController(
            SocietaService service, SquadreSocieta squadre, PartiteSuiCampi partite) {
        this.service = service;
        this.squadre = squadre;
        this.partite = partite;
    }

    @GetMapping("/societa")
    public List<Societa> cerca(@RequestParam(name = "nome", defaultValue = "") String nome) {
        return service.cerca(nome);
    }

    /** Elenco e mappa di tutti i campi: pubblici come la ricerca. */
    @GetMapping("/campi")
    public List<Societa> tuttiICampi() {
        return service.tuttiICampi();
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

    @GetMapping("/societa/{id}")
    public Societa dettaglio(@PathVariable String id) {
        return service.perId(id).orElseThrow(SocietaNonTrovataException::new);
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

    @PostMapping("/societa")
    public ResponseEntity<Societa> inserisci(@Valid @RequestBody NuovoCampoRequest richiesta) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.inserisci(richiesta));
    }
}
