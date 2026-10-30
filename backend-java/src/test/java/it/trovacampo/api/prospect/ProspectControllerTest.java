package it.trovacampo.api.prospect;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.config.ConfigurazioneSicurezza;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** La lista delle ASD/SSD è commerciale: solo con il ruolo trovacampo-admin. */
@WebMvcTest(ProspectController.class)
@Import(ConfigurazioneSicurezza.class)
class ProspectControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ImportazioneProspect importazione;
    @MockitoBean private CollegamentoCampi collegamento;
    @MockitoBean private EsportazioneProspect esportazione;
    @MockitoBean private ProspectRepository repository;

    private static JwtRequestPostProcessor conRuolo(String ruolo) {
        return jwt().jwt(token -> token.claim("preferred_username", "mario"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + ruolo));
    }

    private static JwtRequestPostProcessor admin() {
        return conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE);
    }

    private static ImportazioneProspect.Stato fermo() {
        return new ImportazioneProspect.Stato(false, ImportazioneProspect.Avanzamento.mai(), List.of());
    }

    @Test
    void senzaLoginNienteMappaNeExport() throws Exception {
        mockMvc.perform(get("/api/admin/prospect")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/prospect/export.xlsx")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/prospect/importazione")).andExpect(status().isUnauthorized());
        verify(importazione, never()).avvia(any(), anyString());
    }

    @Test
    void unUtenteSenzaIlRuoloRiceve403() throws Exception {
        mockMvc.perform(get("/api/admin/prospect").with(conRuolo("presenze-utente")))
                .andExpect(status().isForbidden());
    }

    @Test
    void laMappaHaSoloIProspectConLeCoordinate() throws Exception {
        when(repository.conCoordinate())
                .thenReturn(
                        List.of(
                                new Prospect()
                                        .setCodiceFiscale("CF1")
                                        .setDenominazione("A.S.D. VIS SUBIACO")
                                        .setRegione("Lazio")
                                        .setOrganismi(List.of("FIGC"))
                                        .setLat(41.9)
                                        .setLng(13.1)
                                        .setFonteIndirizzo(FonteIndirizzo.CAMPO_TROVACAMPO)));

        mockMvc.perform(get("/api/admin/prospect").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codiceFiscale").value("CF1"))
                .andExpect(jsonPath("$[0].organismi[0]").value("FIGC"))
                .andExpect(jsonPath("$[0].fonteIndirizzo").value("CAMPO_TROVACAMPO"));
    }

    @Test
    void lImportazioneParteInSottofondo() throws Exception {
        when(importazione.avvia(any(), anyString())).thenReturn(true);
        when(importazione.stato()).thenReturn(fermo());

        mockMvc.perform(
                        post("/api/admin/prospect/importazione")
                                .param("regioni", "12")
                                .param("maxPagine", "2")
                                .with(admin()))
                .andExpect(status().isAccepted());

        verify(importazione)
                .avvia(eq(new ImportazioneProspect.Opzioni(List.of(12), 2, false, true)), anyString());
    }

    @Test
    void unaSecondaImportazioneInsiemeRisponde409() throws Exception {
        when(importazione.avvia(any(), anyString())).thenReturn(false);
        when(importazione.stato()).thenReturn(fermo());

        mockMvc.perform(post("/api/admin/prospect/importazione").with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void unaRegioneSconosciutaE400() throws Exception {
        mockMvc.perform(post("/api/admin/prospect/importazione").param("regioni", "99").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lExportScaricaProspectItaliaXlsx() throws Exception {
        mockMvc.perform(get("/api/admin/prospect/export.xlsx").with(admin()))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                "Content-Disposition", "attachment; filename=\"prospect_italia.xlsx\""));
    }
}
