package it.trovacampo.api.indirizzi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'amministrazione degli indirizzi delle società del registro. Sta sotto
 * /api/admin, quindi chiede il ruolo trovacampo-admin
 * ({@link it.trovacampo.api.config.ConfigurazioneSicurezza}).
 *
 * <ul>
 *   <li>{@code POST /arricchimento}: lancia la ricerca per le società di
 *       calcio del Lazio in {@code prospect}, in sottofondo;
 *   <li>{@code GET /arricchimento}: a che punto è;
 *   <li>{@code GET /copertura}: quante hanno l'indirizzo, per fonte;
 *   <li>{@code POST /impianti-open-data}: (re)importa il censimento open data;
 *   <li>{@code POST /trova}: prova la ricerca su una società sola.
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/indirizzi")
public class IndirizziController {

    private static final Logger log = LoggerFactory.getLogger(IndirizziController.class);

    private final ArricchimentoProspect arricchimento;
    private final LocalizzatoreSocieta localizzatore;
    private final ImportatoreImpiantiOpenData importatore;

    public IndirizziController(
            ArricchimentoProspect arricchimento,
            LocalizzatoreSocieta localizzatore,
            ImportatoreImpiantiOpenData importatore) {
        this.arricchimento = arricchimento;
        this.localizzatore = localizzatore;
        this.importatore = importatore;
    }

    /**
     * 202 se parte; 200 con {@code avviato: false} e il motivo se
     * {@code prospect} non esiste ancora; 409 se ne gira già uno.
     */
    @PostMapping("/arricchimento")
    public ResponseEntity<ArricchimentoProspect.Avvio> avvia(
            @RequestParam(defaultValue = "true") boolean geocoding,
            @RequestParam(defaultValue = "false") boolean ritenta,
            @RequestParam(required = false) Integer limite,
            Authentication chi) {
        ArricchimentoProspect.Avvio avvio =
                arricchimento.avvia(new ArricchimentoProspect.Richiesta(geocoding, ritenta, limite));
        if (avvio.avviato()) {
            log.info("Arricchimento indirizzi lanciato da {} (geocoding {}, ritenta {}, limite {})",
                    chi.getName(), geocoding, ritenta, limite);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(avvio);
        }
        return ResponseEntity.status(avvio.stato().inCorso() ? HttpStatus.CONFLICT : HttpStatus.OK).body(avvio);
    }

    @GetMapping("/arricchimento")
    public ArricchimentoProspect.Stato stato() {
        return arricchimento.stato();
    }

    @GetMapping("/copertura")
    public ArricchimentoProspect.Copertura copertura() {
        return arricchimento.copertura();
    }

    @PostMapping("/impianti-open-data")
    public ImportatoreImpiantiOpenData.Esito importaOpenData(Authentication chi) {
        log.info("Importazione del censimento impianti open data lanciata da {}", chi.getName());
        return importatore.importa();
    }

    /** Con {@code geocoding=false} resta sui dati in Mongo e non chiama Nominatim. */
    @PostMapping("/trova")
    public ResponseEntity<IndirizzoMigliore> trova(
            @RequestBody SocietaDaLocalizzare societa,
            @RequestParam(defaultValue = "false") boolean geocoding) {
        return localizzatore
                .trova(societa, new LocalizzatoreSocieta.Opzioni(geocoding))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
