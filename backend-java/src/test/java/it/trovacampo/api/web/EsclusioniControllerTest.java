package it.trovacampo.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.config.ConfigurazioneSicurezza;
import it.trovacampo.api.dominio.Esclusione;
import it.trovacampo.api.service.SocietaService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EsclusioniController.class)
@Import(ConfigurazioneSicurezza.class)
class EsclusioniControllerTest {

    private static final Esclusione POMEZIA =
            new Esclusione(
                    "e1", "pomeziacalcio1957|dadesignare", 40L, 900L, "POMEZIA CALCIO 1957",
                    "DA DESIGNARE (", "XXXXXXXXX", "mario", Instant.parse("2026-09-25T10:00:00Z"));

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SocietaService service;

    private static JwtRequestPostProcessor conRuolo(String ruolo) {
        return jwt().jwt(token -> token.claim("preferred_username", "mario"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + ruolo));
    }

    @Test
    void lAmministratoreVedeLeEsclusioni() throws Exception {
        when(service.esclusioni()).thenReturn(List.of(POMEZIA));

        mockMvc.perform(
                        get("/api/admin/esclusioni")
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("e1"))
                .andExpect(jsonPath("$[0].nomeSocieta").value("POMEZIA CALCIO 1957"))
                .andExpect(jsonPath("$[0].esclusaDa").value("mario"));
    }

    @Test
    void lAmministratoreAnnullaUnEsclusione() throws Exception {
        when(service.annullaEsclusione("e1")).thenReturn(Optional.of(POMEZIA));

        mockMvc.perform(
                        delete("/api/admin/esclusioni/e1")
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isNoContent());
    }

    @Test
    void annullareUnEsclusioneCheNonCeRisponde404() throws Exception {
        when(service.annullaEsclusione("x")).thenReturn(Optional.empty());

        mockMvc.perform(
                        delete("/api/admin/esclusioni/x")
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void senzaLoginNienteElencoNeAnnullamento() throws Exception {
        mockMvc.perform(get("/api/admin/esclusioni")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/esclusioni/e1")).andExpect(status().isUnauthorized());

        verify(service, never()).annullaEsclusione(any());
    }

    @Test
    void senzaIlRuoloRisponde403() throws Exception {
        mockMvc.perform(get("/api/admin/esclusioni").with(conRuolo("allenatore")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/esclusioni/e1").with(conRuolo("allenatore")))
                .andExpect(status().isForbidden());

        verify(service, never()).esclusioni();
        verify(service, never()).annullaEsclusione(any());
    }
}
