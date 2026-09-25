package it.trovacampo.api.web;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.service.SocietaService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Creazione, modifica e cancellazione della scheda di una società. Sta sotto
 * /api/admin, quindi {@link it.trovacampo.api.config.ConfigurazioneSicurezza}
 * chiede il login sul Keycloak di presenze con il ruolo trovacampo-admin.
 */
@RestController
@RequestMapping("/api/admin/societa")
public class AmministrazioneSocietaController {

    private static final Logger log = LoggerFactory.getLogger(AmministrazioneSocietaController.class);

    private final SocietaService service;

    public AmministrazioneSocietaController(SocietaService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    public Societa modifica(
            @PathVariable String id,
            @Valid @RequestBody ModificaSocietaRequest richiesta,
            Authentication chi) {
        Societa salvata = service.modifica(id, richiesta).orElseThrow(SocietaNonTrovataException::new);
        // L'unica traccia di chi ha cambiato una scheda, se un giorno ci si
        // chiede da dove arriva un dato.
        log.info("Scheda {} ({}) modificata da {}", id, salvata.getNomeSocieta(), chi.getName());
        return salvata;
    }

    @PostMapping
    public ResponseEntity<Societa> crea(
            @Valid @RequestBody ModificaSocietaRequest richiesta, Authentication chi) {
        Societa creata = service.crea(richiesta);
        log.info("Scheda {} ({}) creata da {}", creata.getId(), creata.getNomeSocieta(), chi.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creata);
    }

    /**
     * Una scheda che viene da presenze lascia un'esclusione, così la
     * sincronizzazione non la ricrea: vedi {@link SocietaService#elimina}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> elimina(@PathVariable String id, Authentication chi) {
        SocietaService.Eliminazione eliminata =
                service.elimina(id, chi.getName()).orElseThrow(SocietaNonTrovataException::new);
        log.info(
                "Scheda {} ({} - {}) eliminata da {}{}",
                id,
                eliminata.societa().getNomeSocieta(),
                eliminata.societa().getNomeImpianto(),
                chi.getName(),
                eliminata.esclusa() ? ", esclusa dalla sincronizzazione" : "");
        return ResponseEntity.noContent().build();
    }
}
