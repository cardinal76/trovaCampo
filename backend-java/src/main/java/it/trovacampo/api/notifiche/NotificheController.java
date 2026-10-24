package it.trovacampo.api.notifiche;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final ProssimePartite prossime;

    public NotificheController(
            IscrizioniService iscrizioni, DepositoChiaviVapid chiavi, ProssimePartite prossime) {
        this.iscrizioni = iscrizioni;
        this.chiavi = chiavi;
        this.prossime = prossime;
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

    /**
     * Le partite della settimana per cui arriverebbe un avviso, con le
     * scelte che manda il browser (squadre seguite, posizione e raggio):
     * per la pagina Prossime partite. POST e non GET perché le scelte stanno
     * nel corpo, non nell'indirizzo che finisce nei log di Caddy; non salva
     * niente.
     */
    @PostMapping("/prossime")
    public ProssimePartite.Elenco prossime(@Valid @RequestBody ProssimeRequest richiesta) {
        return prossime.elenco(richiesta);
    }

    @DeleteMapping("/iscrizione")
    public ResponseEntity<Void> cancella(@Valid @RequestBody CancellazioneRequest richiesta) {
        iscrizioni.cancella(richiesta.endpoint());
        return ResponseEntity.noContent().build();
    }
}
