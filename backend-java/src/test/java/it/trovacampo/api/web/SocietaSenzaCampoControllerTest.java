package it.trovacampo.api.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.anagrafica.SocietaSenzaCampo;
import it.trovacampo.api.config.ConfigurazioneSicurezza;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SocietaSenzaCampoController.class)
@Import(ConfigurazioneSicurezza.class)
class SocietaSenzaCampoControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SocietaSenzaCampo societa;

    @Test
    void lAmministratoreVedeLeSocietaSenzaCampo() throws Exception {
        when(societa.elenco())
                .thenReturn(List.of(new SocietaSenzaCampo.Voce(383L, "ASDPOL ZAGAROLO CALCIO", null)));

        mockMvc.perform(
                        get("/api/admin/societa-senza-campo")
                                .with(jwt().authorities(new SimpleGrantedAuthority(
                                        "ROLE_" + ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].anagraficaSocietaId").value(383))
                .andExpect(jsonPath("$[0].denominazione").value("ASDPOL ZAGAROLO CALCIO"));
    }

    @Test
    void senzaLoginNo() throws Exception {
        mockMvc.perform(get("/api/admin/societa-senza-campo")).andExpect(status().isUnauthorized());
        verifyNoInteractions(societa);
    }
}
