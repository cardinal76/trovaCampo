package it.trovacampo.api.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.service.SocietaService;
import it.trovacampo.api.web.AmministrazioneSocietaController;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Il CORS deve permettere i metodi che l'amministrazione usa davvero.
 *
 * <p>Attenzione al caso del sito: anche se le pagine e l'API stanno sullo
 * stesso dominio, il browser aggiunge {@code Origin} a ogni richiesta che non
 * sia GET o HEAD, e Spring tratta come CORS qualunque richiesta con
 * {@code Origin}. Se il metodo non è fra quelli permessi la PUT viene
 * respinta con 403 "Invalid CORS request" senza arrivare al controller, anche
 * con il token e il ruolo giusti e senza nessuna preflight di mezzo.
 */
@WebMvcTest(AmministrazioneSocietaController.class)
@Import({ConfigurazioneSicurezza.class, ConfigurazioneCors.class})
class ConfigurazioneCorsTest {

    private static final String ORIGINE = "https://trovacampo.footballer.it";

    private static final String MODULO =
            """
            {"siglaSocieta":"A.S.D.","nomeSocieta":"Certosa Calcio","nomeImpianto":"Campo Certosa",
             "indirizzoImpianto":"Via della Certosa 12","localitaImpianto":"Roma"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SocietaService service;

    @Test
    void laModificaDalSitoArrivaAlControllerNonostanteLOrigin() throws Exception {
        when(service.modifica(eq("1"), any()))
                .thenReturn(Optional.of(new Societa().setId("1").setNomeSocieta("Certosa Calcio")));

        mockMvc.perform(
                        put("/api/admin/societa/1")
                                .header(HttpHeaders.ORIGIN, ORIGINE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(MODULO)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_"
                                                                        + ConfigurazioneSicurezza
                                                                                .RUOLO_AMMINISTRATORE))))
                .andExpect(status().isOk());
    }

    @Test
    void permetteLaPreflightDellaModifica() throws Exception {
        preflight("/api/admin/societa/1", "PUT").andExpect(status().isOk());
    }

    @Test
    void permetteLaPreflightDellEliminazione() throws Exception {
        preflight("/api/admin/societa/1", "DELETE").andExpect(status().isOk());
    }

    @Test
    void permetteLaPreflightDellaRicerca() throws Exception {
        preflight("/api/societa", "GET").andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions preflight(
            String percorso, String metodo) throws Exception {
        return mockMvc.perform(
                options(percorso)
                        .header(HttpHeaders.ORIGIN, ORIGINE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, metodo));
    }
}
