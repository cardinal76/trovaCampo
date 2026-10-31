package it.trovacampo.api.indirizzi;

import it.trovacampo.api.web.DatiNonValidiException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
 *   <li>{@code POST /impianti-open-data?fonte=cmrc|veneto}: (re)importa un
 *       censimento open data degli impianti ({@link FonteOpenData}); senza
 *       {@code fonte}, quello di Roma. Il Veneto sono una decina di file a
 *       dieci secondi l'uno dall'altro: la risposta arriva dopo un paio di minuti;
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
    public ImportatoreImpiantiOpenData.Esito importaOpenData(
            Authentication chi, @RequestParam(defaultValue = "cmrc") String fonte) {
        FonteOpenData scelta =
                FonteOpenData.daParametro(fonte)
                        .orElseThrow(
                                () -> new DatiNonValidiException(
                                        "Fonte open data sconosciuta: " + fonte + " (si può scegliere fra "
                                                + Arrays.stream(FonteOpenData.values())
                                                        .map(FonteOpenData::parametro)
                                                        .collect(Collectors.joining(", "))
                                                + ")"));
        log.info("Importazione degli impianti open data {} lanciata da {}", scelta.parametro(), chi.getName());
        return importatore.importa(scelta);
    }

    /** Il portale ha risposto come chi blocca: si dice, senza riprovare. */
    @ExceptionHandler(ImportatoreImpiantiOpenData.OpenDataBloccatoException.class)
    public ResponseEntity<Map<String, String>> bloccato(ImportatoreImpiantiOpenData.OpenDataBloccatoException e) {
        log.warn("Importazione open data fermata: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("errore", e.getMessage()));
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
