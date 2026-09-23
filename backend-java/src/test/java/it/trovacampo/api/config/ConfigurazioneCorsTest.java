package it.trovacampo.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.service.SocietaService;
import it.trovacampo.api.web.AmministrazioneSocietaController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Il browser manda una preflight prima di ogni PUT e DELETE verso un'altra
 * origine (app Capacitor, o sito servito da un dominio diverso dall'API):
 * se il metodo non è fra quelli permessi la richiesta muore lì, con 403,
 * prima ancora di arrivare al controller.
 */
@WebMvcTest(AmministrazioneSocietaController.class)
@Import({ConfigurazioneSicurezza.class, ConfigurazioneCors.class})
class ConfigurazioneCorsTest {

    private static final String ORIGINE = "https://trovacampo.footballer.it";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SocietaService service;

    @Test
    void permetteLaModificaDiUnaScheda() throws Exception {
        mockMvc.perform(
                        options("/api/admin/societa/1")
                                .header(HttpHeaders.ORIGIN, ORIGINE)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isOk());
    }

    @Test
    void permetteLEliminazioneDiUnaScheda() throws Exception {
        mockMvc.perform(
                        options("/api/admin/societa/1")
                                .header(HttpHeaders.ORIGIN, ORIGINE)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isOk());
    }

    @Test
    void permetteLaRicerca() throws Exception {
        mockMvc.perform(
                        options("/api/societa")
                                .header(HttpHeaders.ORIGIN, ORIGINE)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk());
    }
}
