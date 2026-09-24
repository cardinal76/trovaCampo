package it.trovacampo.api.notifiche;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le notifiche push, senza login come il resto dell'app pubblica: chi le
 * accende è un browser, riconosciuto dal suo endpoint push. Le richieste
 * troppo grandi si fermano prima, in {@link LimiteRichiesteNotifiche}.
 */
@RestController
@RequestMapping("/api/notifiche")
public class NotificheController {

    private final IscrizioniService iscrizioni;
    private final DepositoChiaviVapid chiavi;

    public NotificheController(IscrizioniService iscrizioni, DepositoChiaviVapid chiavi) {
        this.iscrizioni = iscrizioni;
        this.chiavi = chiavi;
    }

    /** La chiave pubblica VAPID, che il browser vuole per iscriversi. */
    @GetMapping("/chiave")
    public Map<String, String> chiave() {
        return Map.of("chiave", chiavi.chiavi().pubblicaBase64());
    }

    /**
     * Crea o aggiorna l'iscrizione: il sito la manda quando si accende un
     * avviso, quando si cambia una preferenza o si segue una squadra, e a ogni
     * apertura (con la posizione nuova, se il permesso c'è già).
     */
    @PutMapping("/iscrizione")
    public ResponseEntity<Void> salva(@Valid @RequestBody IscrizioneRequest richiesta) {
        iscrizioni.salva(richiesta);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/iscrizione")
    public ResponseEntity<Void> cancella(@Valid @RequestBody CancellazioneRequest richiesta) {
        iscrizioni.cancella(richiesta.endpoint());
        return ResponseEntity.noContent().build();
    }
}
