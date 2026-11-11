package it.trovacampo.api.segnalazioni;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * «Hai dati da rettificare?»: l'invio e' pubblico, senza login; l'elenco sta
 * sotto /api/admin e lo vede solo chi amministra.
 */
@RestController
@RequestMapping("/api")
public class SegnalazioniController {

    private final SegnalazioniService service;
    private final SegnalazioniRepository repository;

    public SegnalazioniController(SegnalazioniService service, SegnalazioniRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping("/segnalazioni")
    public ResponseEntity<Void> invia(@Valid @RequestBody SegnalazioneRequest richiesta) {
        if (richiesta.sito() != null && !richiesta.sito().isBlank()) {
            // Casella-trappola riempita: finta riuscita, niente salvato.
            return ResponseEntity.noContent().build();
        }
        return service.registra(richiesta)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    @GetMapping("/admin/segnalazioni")
    public List<Segnalazione> elenco() {
        return repository.findAllByOrderByRicevutaIlDesc();
    }

    /** Segna una segnalazione come gestita (o la riapre). */
    @PutMapping("/admin/segnalazioni/{id}")
    public ResponseEntity<Segnalazione> gestita(
            @PathVariable String id, @RequestBody StatoSegnalazione stato) {
        return repository
                .findById(id)
                .map(
                        s ->
                                repository.save(
                                        new Segnalazione(
                                                s.id(),
                                                s.tipo(),
                                                s.societaId(),
                                                s.attuale(),
                                                s.corretto(),
                                                s.contatto(),
                                                s.ricevutaIl(),
                                                stato.gestita())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record StatoSegnalazione(boolean gestita) {}
}
