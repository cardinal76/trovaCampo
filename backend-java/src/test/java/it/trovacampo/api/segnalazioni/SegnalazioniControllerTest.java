package it.trovacampo.api.segnalazioni;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.sicurezza.ConfigurazioneSicurezza;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SegnalazioniController.class)
@Import({ConfigurazioneSicurezza.class, SegnalazioniService.class})
class SegnalazioniControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SegnalazioniRepository repository;

    private static String corpo(String corretto, String extra) {
        return "{\"tipo\":\"NOME_SOCIETA\",\"corretto\":\"" + corretto + "\"" + extra + "}";
    }

    @Test
    void l_invio_e_pubblico_e_salva() throws Exception {
        mockMvc.perform(
                        post("/api/segnalazioni")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("A.S.D. Nuova", ",\"contatto\":\"a@b.it\"")))
                .andExpect(status().isNoContent());
        verify(repository).save(any(Segnalazione.class));
    }

    @Test
    void senza_il_dato_corretto_e_400() throws Exception {
        mockMvc.perform(
                        post("/api/segnalazioni")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(" ", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void email_non_valida_e_400() throws Exception {
        mockMvc.perform(
                        post("/api/segnalazioni")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("x", ",\"contatto\":\"non-una-mail\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void la_casella_trappola_riempita_non_salva() throws Exception {
        mockMvc.perform(
                        post("/api/segnalazioni")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("x", ",\"sito\":\"http://spam\"")))
                .andExpect(status().isNoContent());
        verify(repository, never()).save(any());
    }

    @Test
    void col_tetto_orario_pieno_e_429() throws Exception {
        when(repository.countByRicevutaIlAfter(any())).thenReturn((long) SegnalazioniService.MASSIMO_ALL_ORA);
        mockMvc.perform(
                        post("/api/segnalazioni")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo("x", "")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void l_elenco_senza_login_e_401() throws Exception {
        mockMvc.perform(get("/api/admin/segnalazioni")).andExpect(status().isUnauthorized());
    }

    @Test
    void segnare_come_gestita_senza_login_e_401() throws Exception {
        mockMvc.perform(
                        put("/api/admin/segnalazioni/abc")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"gestita\":true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void l_admin_segna_come_gestita() throws Exception {
        Segnalazione s =
                new Segnalazione("abc", TipoRettifica.ALTRO, null, null, "x", null, Instant.now(), false);
        when(repository.findById("abc")).thenReturn(Optional.of(s));
        when(repository.save(any(Segnalazione.class))).thenAnswer(i -> i.getArgument(0));
        mockMvc.perform(
                        put("/api/admin/segnalazioni/abc")
                                .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                "ROLE_" + ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"gestita\":true}"))
                .andExpect(status().isOk());
    }
}
