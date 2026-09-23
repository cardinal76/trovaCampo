package it.trovacampo.api.web;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.service.SocietaService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Modifica della scheda di una società. Sta sotto /api/admin, quindi
 * {@link it.trovacampo.api.config.ConfigurazioneSicurezza} chiede il login sul
 * Keycloak di presenze con il ruolo trovacampo-admin.
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
}
