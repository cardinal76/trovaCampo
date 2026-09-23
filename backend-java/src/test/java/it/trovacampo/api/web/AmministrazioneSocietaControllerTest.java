package it.trovacampo.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.config.ConfigurazioneSicurezza;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.service.SocietaService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AmministrazioneSocietaController.class)
@Import(ConfigurazioneSicurezza.class)
class AmministrazioneSocietaControllerTest {

    private static final String MODULO =
            """
            {"siglaSocieta":"A.S.D.","nomeSocieta":"Certosa Calcio","nomeImpianto":"Campo Certosa",
             "indirizzoImpianto":"Via della Certosa 12","localitaImpianto":"Roma","email":"info@certosa.it"}
            """;

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SocietaService service;

    private static JwtRequestPostProcessor conRuolo(String ruolo) {
        return jwt().jwt(token -> token.claim("preferred_username", "mario"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + ruolo));
    }

    @Test
    void lAmministratoreModificaLaScheda() throws Exception {
        when(service.modifica(eq("1"), any()))
                .thenReturn(Optional.of(new Societa().setId("1").setNomeSocieta("Certosa Calcio")));

        mockMvc.perform(
                        put("/api/admin/societa/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(MODULO)
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeSocieta").value("Certosa Calcio"));
    }

    @Test
    void senzaLoginRisponde401() throws Exception {
        mockMvc.perform(put("/api/admin/societa/1").contentType(MediaType.APPLICATION_JSON).content(MODULO))
                .andExpect(status().isUnauthorized());

        verify(service, never()).modifica(any(), any());
    }

    @Test
    void senzaIlRuoloRisponde403() throws Exception {
        mockMvc.perform(
                        put("/api/admin/societa/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(MODULO)
                                .with(conRuolo("allenatore")))
                .andExpect(status().isForbidden());

        verify(service, never()).modifica(any(), any());
    }

    @Test
    void rifiutaUnModuloSenzaIDatiObbligatori() throws Exception {
        mockMvc.perform(
                        put("/api/admin/societa/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nomeSocieta\":\" \",\"email\":\"non-una-mail\"}")
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errore")
                                .value(
                                        "email non valida, indirizzoImpianto è obbligatorio,"
                                                + " nomeImpianto è obbligatorio, nomeSocieta è obbligatorio"));
    }

    @Test
    void unaSocietaCheNonEsisteRisponde404() throws Exception {
        when(service.modifica(eq("x"), any())).thenReturn(Optional.empty());

        mockMvc.perform(
                        put("/api/admin/societa/x")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(MODULO)
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isNotFound());
    }
}
