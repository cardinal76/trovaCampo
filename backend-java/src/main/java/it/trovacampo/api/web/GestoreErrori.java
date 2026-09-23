package it.trovacampo.api.web;

import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Errori nella forma {"errore": "..."} usata anche dall'API Node. */
@RestControllerAdvice
public class GestoreErrori {

    private static final Logger log = LoggerFactory.getLogger(GestoreErrori.class);

    @ExceptionHandler(SocietaNonTrovataException.class)
    public ResponseEntity<Map<String, String>> nonTrovata(SocietaNonTrovataException eccezione) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("errore", eccezione.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> datiNonValidi(
            MethodArgumentNotValidException eccezione) {
        String messaggio =
                eccezione.getBindingResult().getFieldErrors().stream()
                        .map(errore -> errore.getDefaultMessage())
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest().body(Map.of("errore", messaggio));
    }

    @ExceptionHandler(DatiNonValidiException.class)
    public ResponseEntity<Map<String, String>> datiIncoerenti(DatiNonValidiException eccezione) {
        return ResponseEntity.badRequest().body(Map.of("errore", eccezione.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> fileTroppoGrande(
            MaxUploadSizeExceededException eccezione) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("errore", "Il file supera i 20 MB"));
    }

    /**
     * L'unico servizio che TrovaCampo chiama e che può far fallire una
     * richiesta è l'anagrafica di presenze (Nominatim i suoi errori li
     * assorbe): giù durante un suo aggiornamento, o irraggiungibile.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> anagraficaNonRaggiungibile(
            RestClientException eccezione) {
        log.warn("Anagrafica di presenze non raggiungibile: {}", eccezione.toString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("errore", "L'anagrafica di presenze non risponde: riprova tra poco"));
    }
}
