package it.trovacampo.api.indirizzi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.config.ConfigurazioneSicurezza;
import java.util.Map;
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

    @MockitoBean private ArricchimentoProspect arricchimento;
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
        mockMvc.perform(post("/api/admin/indirizzi/arricchimento")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/indirizzi/copertura")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/indirizzi/arricchimento").with(conRuolo("presenze-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void senzaProspectRispondeConUnMessaggioChiaro() throws Exception {
        when(arricchimento.avvia(any()))
                .thenReturn(new ArricchimentoProspect.Avvio(
                        false, "La collection prospect non esiste ancora", ArricchimentoProspect.Stato.MAI_PARTITO));

        mockMvc.perform(post("/api/admin/indirizzi/arricchimento").with(amministratore()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avviato").value(false))
                .andExpect(jsonPath("$.messaggio").value("La collection prospect non esiste ancora"));
    }

    @Test
    void lArricchimentoPartiteInSottofondo() throws Exception {
        when(arricchimento.avvia(new ArricchimentoProspect.Richiesta(false, false, 50)))
                .thenReturn(new ArricchimentoProspect.Avvio(
                        true, "Arricchimento avviato",
                        new ArricchimentoProspect.Stato(true, null, null, 0, 0, 0, 0, false, "In preparazione")));

        mockMvc.perform(post("/api/admin/indirizzi/arricchimento?geocoding=false&limite=50").with(amministratore()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.avviato").value(true));
    }

    @Test
    void laCoperturaPerFonte() throws Exception {
        when(arricchimento.copertura())
                .thenReturn(new ArricchimentoProspect.Copertura(
                        true, "Società di calcio del Lazio", 10, 10, 7, 6, 0.7, 0.7,
                        Map.of(Fonte.CAMPI_LND, 5, Fonte.OPEN_DATA, 2), Map.of(Confidenza.ALTA, 5), true,
                        "regione = Lazio"));

        mockMvc.perform(get("/api/admin/indirizzi/copertura").with(amministratore()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conIndirizzo").value(7))
                .andExpect(jsonPath("$.perFonte.CAMPI_LND").value(5));
    }

    @Test
    void laProvaSuUnaSocieta() throws Exception {
        when(localizzatore.trova(new SocietaDaLocalizzare("A.S.D. ROMULEA", "ROMA", null, null),
                        LocalizzatoreSocieta.Opzioni.SENZA_RETE))
                .thenReturn(Optional.of(new IndirizzoMigliore(
                        "VIA FARSALO 21", "ROMA", 41.88, 12.5, Fonte.CAMPI_LND, Confidenza.ALTA, 1, "SS ROMULEA",
                        "ROMULEA", Fonte.CAMPI_LND.attribuzione())));

        mockMvc.perform(post("/api/admin/indirizzi/trova")
                        .with(amministratore())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"denominazione\":\"A.S.D. ROMULEA\",\"comune\":\"ROMA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indirizzo").value("VIA FARSALO 21"))
                .andExpect(jsonPath("$.fonte").value("CAMPI_LND"))
                .andExpect(jsonPath("$.confidenza").value("ALTA"));

        mockMvc.perform(post("/api/admin/indirizzi/trova")
                        .with(amministratore())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"denominazione\":\"NESSUNA\",\"comune\":\"ROMA\"}"))
                .andExpect(status().isNoContent());
    }
}
