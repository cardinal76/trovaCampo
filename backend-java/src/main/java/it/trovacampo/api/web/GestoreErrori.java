package it.trovacampo.api.web;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Errori nella forma {"errore": "..."} usata anche dall'API Node. */
@RestControllerAdvice
public class GestoreErrori {

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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> fileTroppoGrande(
            MaxUploadSizeExceededException eccezione) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("errore", "Il file supera i 20 MB"));
    }
}
