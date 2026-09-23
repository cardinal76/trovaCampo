package it.trovacampo.api.importazione;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Caricamento del file Excel. Non è per gli utenti dell'app: lo usa chi
 * amministra, con un token condiviso nell'intestazione {@value #INTESTAZIONE}.
 *
 * <p>Senza token configurato l'endpoint risponde 404, come se non esistesse:
 * un'installazione dimenticata senza token non resta aperta a chiunque.
 *
 * <pre>
 * curl -H "X-Token-Importazione: $TOKEN" -F file=@campi.xlsx \
 *      "https://trovacampo.footballer.it/api/admin/importazione?prova=true"
 * </pre>
 */
@RestController
@RequestMapping("/api/admin")
public class ImportazioneController {

    static final String INTESTAZIONE = "X-Token-Importazione";

    private final ImportazioneService service;
    private final byte[] token;

    public ImportazioneController(
            ImportazioneService service,
            @Value("${trovacampo.importazione.token:}") String token) {
        this.service = service;
        this.token = token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping(path = "/importazione", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importa(
            @RequestHeader(name = INTESTAZIONE, required = false) String tokenRicevuto,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "prova", defaultValue = "false") boolean prova)
            throws IOException {
        if (token.length == 0) {
            return ResponseEntity.notFound().build();
        }
        // Confronto a tempo costante: con equals() la durata della risposta
        // rivelerebbe quanti caratteri iniziali del token sono giusti.
        byte[] ricevuto =
                tokenRicevuto == null ? new byte[0] : tokenRicevuto.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(token, ricevuto)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("errore", "Token di importazione mancante o errato"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errore", "Il file è vuoto"));
        }

        try (InputStream contenuto = file.getInputStream()) {
            return ResponseEntity.ok(service.importa(contenuto, prova));
        }
    }

    @ExceptionHandler(LettoreExcel.FileNonValidoException.class)
    public ResponseEntity<Map<String, String>> fileNonValido(LettoreExcel.FileNonValidoException e) {
        return ResponseEntity.badRequest().body(Map.of("errore", e.getMessage()));
    }
}
