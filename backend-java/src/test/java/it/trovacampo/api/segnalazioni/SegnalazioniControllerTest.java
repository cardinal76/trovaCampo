package it.trovacampo.api.segnalazioni;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.sicurezza.ConfigurazioneSicurezza;
import org.junit.jupiter.api.Test;
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
}
