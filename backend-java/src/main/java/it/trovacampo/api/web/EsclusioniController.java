package it.trovacampo.api.web;

import it.trovacampo.api.dominio.Esclusione;
import it.trovacampo.api.service.SocietaService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * I campi di presenze eliminati da chi amministra, che la sincronizzazione
 * non ricrea più: l'elenco, per la pagina /admin/importazione, e
 * l'annullamento di un'esclusione fatta per sbaglio. Sta sotto /api/admin,
 * quindi serve il ruolo trovacampo-admin come per il resto.
 */
@RestController
@RequestMapping("/api/admin/esclusioni")
public class EsclusioniController {

    private static final Logger log = LoggerFactory.getLogger(EsclusioniController.class);

    private final SocietaService service;

    public EsclusioniController(SocietaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Esclusione> elenco() {
        return service.esclusioni();
    }

    /** Il campo torna alla sincronizzazione successiva, se presenze lo manda ancora. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> annulla(@PathVariable String id, Authentication chi) {
        return service.annullaEsclusione(id)
                .<ResponseEntity<?>>map(
                        esclusione -> {
                            log.info(
                                    "Esclusione di {} - {} annullata da {}",
                                    esclusione.nomeSocieta(),
                                    esclusione.nomeImpianto(),
                                    chi.getName());
                            return ResponseEntity.noContent().build();
                        })
                .orElseGet(
                        () ->
                                ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(Map.of("errore", "Esclusione non trovata")));
    }
}
