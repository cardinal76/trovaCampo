package it.trovacampo.api.indirizzi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le fonti degli indirizzi, per chi amministra (ruolo trovacampo-admin, vedi
 * {@link it.trovacampo.api.config.ConfigurazioneSicurezza}). Il collegamento
 * dei prospect e la copertura stanno in {@code /api/admin/prospect}.
 *
 * <ul>
 *   <li>{@code POST /impianti-open-data}: (re)importa il censimento open data;
 *   <li>{@code POST /trova}: prova la ricerca su una società sola.
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/indirizzi")
public class IndirizziController {

    private static final Logger log = LoggerFactory.getLogger(IndirizziController.class);

    private final LocalizzatoreSocieta localizzatore;
    private final ImportatoreImpiantiOpenData importatore;

    public IndirizziController(LocalizzatoreSocieta localizzatore, ImportatoreImpiantiOpenData importatore) {
        this.localizzatore = localizzatore;
        this.importatore = importatore;
    }

    @PostMapping("/impianti-open-data")
    public ImportatoreImpiantiOpenData.Esito importaOpenData(Authentication chi) {
        log.info("Importazione del censimento impianti open data lanciata da {}", chi.getName());
        return importatore.importa();
    }

    /** Con {@code geocoding=false} resta sui dati che ha e non chiama Nominatim. */
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
