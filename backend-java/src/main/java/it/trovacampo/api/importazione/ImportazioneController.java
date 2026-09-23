package it.trovacampo.api.importazione;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Caricamento del file Excel, dalla pagina /admin/importazione dell'app.
 * L'accesso lo decide {@link it.trovacampo.api.config.ConfigurazioneSicurezza}:
 * serve il login sul Keycloak di presenze con il ruolo trovacampo-admin.
 */
@RestController
@RequestMapping("/api/admin")
public class ImportazioneController {

    private static final Logger log = LoggerFactory.getLogger(ImportazioneController.class);

    private final ImportazioneService service;
    private final SincronizzazioneAnagrafica sincronizzazione;

    public ImportazioneController(
            ImportazioneService service, SincronizzazioneAnagrafica sincronizzazione) {
        this.service = service;
        this.sincronizzazione = sincronizzazione;
    }

    @PostMapping(path = "/importazione", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importa(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "prova", defaultValue = "false") boolean prova,
            Authentication chi)
            throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errore", "Il file è vuoto"));
        }

        try (InputStream contenuto = file.getInputStream()) {
            EsitoImportazione esito = service.importa(contenuto, prova);
            if (!prova) {
                // Chi ha scritto nell'archivio, e cosa: l'unica traccia se un
                // giorno ci si chiede da dove arriva una riga.
                log.info(
                        "Importazione di '{}' da {}: {} inserite, {} aggiornate",
                        file.getOriginalFilename(),
                        chi.getName(),
                        esito.inserite(),
                        esito.aggiornate());
            }
            return ResponseEntity.ok(esito);
        }
    }

    /**
     * I campi dell'anagrafica di presenze, adesso invece che al prossimo giro
     * programmato. Con {@code prova} non si salva niente, come per un file.
     */
    @PostMapping("/anagrafica")
    public EsitoImportazione sincronizza(
            @RequestParam(name = "prova", defaultValue = "false") boolean prova, Authentication chi) {
        EsitoImportazione esito = sincronizzazione.sincronizza(prova);
        if (!prova) {
            log.info(
                    "Anagrafica di presenze importata da {}: {} inserite, {} aggiornate",
                    chi.getName(),
                    esito.inserite(),
                    esito.aggiornate());
        }
        return esito;
    }

    @ExceptionHandler(LettoreExcel.FileNonValidoException.class)
    public ResponseEntity<Map<String, String>> fileNonValido(LettoreExcel.FileNonValidoException e) {
        return ResponseEntity.badRequest().body(Map.of("errore", e.getMessage()));
    }
}
