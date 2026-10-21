package it.trovacampo.api.web;

import it.trovacampo.api.anagrafica.SocietaSenzaCampo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le società senza campo, per chi amministra: sta sotto /api/admin, quindi
 * serve il ruolo trovacampo-admin. Vedi {@link SocietaSenzaCampo}.
 */
@RestController
public class SocietaSenzaCampoController {

    private final SocietaSenzaCampo societa;

    public SocietaSenzaCampoController(SocietaSenzaCampo societa) {
        this.societa = societa;
    }

    @GetMapping("/api/admin/societa-senza-campo")
    public List<SocietaSenzaCampo.Voce> elenco() {
        return societa.elenco();
    }
}
