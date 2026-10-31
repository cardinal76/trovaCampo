package it.trovacampo.api.indirizzi;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.config.ConfigurazioneSicurezza;
import java.util.List;
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

@WebMvcTest(IndirizziController.class)
@Import(ConfigurazioneSicurezza.class)
class IndirizziControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LocalizzatoreSocieta localizzatore;
    @MockitoBean private ImportatoreImpiantiOpenData importatore;

    private static JwtRequestPostProcessor conRuolo(String ruolo) {
        return jwt().jwt(token -> token.claim("preferred_username", "mario"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + ruolo));
    }

    private static JwtRequestPostProcessor amministratore() {
        return conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE);
    }

    @Test
    void senzaLoginOSenzaRuoloNiente() throws Exception {
        mockMvc.perform(post("/api/admin/indirizzi/impianti-open-data")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/indirizzi/impianti-open-data").with(conRuolo("presenze-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void importaIlCensimento() throws Exception {
        when(importatore.importa(FonteOpenData.CMRC))
                .thenReturn(new ImportatoreImpiantiOpenData.Esito(
                        "cmrc-censimento", "CC0 1.0", "https://x", 6636, 1007, 480, 0, List.of()));

        // Senza fonte, Roma: come prima che ci fossero le altre.
        mockMvc.perform(post("/api/admin/indirizzi/impianti-open-data").with(amministratore()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impianti").value(1007))
                .andExpect(jsonPath("$.licenza").value("CC0 1.0"));
    }

    @Test
    void importaIlVenetoConIlParametroDellaFonte() throws Exception {
        when(importatore.importa(FonteOpenData.VENETO))
                .thenReturn(new ImportatoreImpiantiOpenData.Esito(
                        "veneto-comuni", "CC BY 4.0", "https://dati.veneto.it", 180, 97, 60, 67,
                        List.of("Comune di Valdagno - Impianti Sportivi: timeout")));

        mockMvc.perform(post("/api/admin/indirizzi/impianti-open-data?fonte=veneto").with(amministratore()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("veneto-comuni"))
                .andExpect(jsonPath("$.licenza").value("CC BY 4.0"))
                .andExpect(jsonPath("$.conCoordinate").value(67))
                .andExpect(jsonPath("$.nonLetti[0]").value("Comune di Valdagno - Impianti Sportivi: timeout"));
    }

    @Test
    void unaFonteSconosciutaE400() throws Exception {
        mockMvc.perform(post("/api/admin/indirizzi/impianti-open-data?fonte=marte").with(amministratore()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value(org.hamcrest.Matchers.containsString("cmrc, veneto")));
        verifyNoInteractions(importatore);
    }

    @Test
    void unPortaleCheBloccaE503ConIlMotivo() throws Exception {
        when(importatore.importa(FonteOpenData.VENETO))
                .thenThrow(new ImportatoreImpiantiOpenData.OpenDataBloccatoException(
                        "dati.veneto.it ha risposto 429: importazione fermata, senza riprovare."));

        mockMvc.perform(post("/api/admin/indirizzi/impianti-open-data?fonte=veneto").with(amministratore()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errore").value(org.hamcrest.Matchers.containsString("429")));
    }

    @Test
    void laProvaSuUnaSocieta() throws Exception {
        when(localizzatore.trova(new SocietaDaLocalizzare("A.S.D. ROMULEA", "ROMA", null),
                        LocalizzatoreSocieta.Opzioni.SENZA_RETE))
                .thenReturn(Optional.of(new IndirizzoMigliore(
                        "VIA FARSALO 21", "ROMA", 41.88, 12.5, FonteIndirizzo.CAMPO_TROVACAMPO, Confidenza.ALTA, 1,
                        "SS ROMULEA", "ROMULEA", "nome")));

        mockMvc.perform(post("/api/admin/indirizzi/trova")
                        .with(amministratore())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"denominazione\":\"A.S.D. ROMULEA\",\"comune\":\"ROMA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indirizzo").value("VIA FARSALO 21"))
                .andExpect(jsonPath("$.fonte").value("CAMPO_TROVACAMPO"))
                .andExpect(jsonPath("$.confidenza").value("ALTA"));

        mockMvc.perform(post("/api/admin/indirizzi/trova")
                        .with(amministratore())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"denominazione\":\"NESSUNA\",\"comune\":\"ROMA\"}"))
                .andExpect(status().isNoContent());
    }
}
