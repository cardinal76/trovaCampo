package it.trovacampo.api.importazione;

import static it.trovacampo.api.importazione.FileExcel.conRighe;
import static it.trovacampo.api.importazione.FileExcel.riga;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.config.ConfigurazioneSicurezza;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

@WebMvcTest(ImportazioneController.class)
@Import(ConfigurazioneSicurezza.class)
class ImportazioneControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ImportazioneService service;

    @MockitoBean private SincronizzazioneAnagrafica sincronizzazione;

    private static MockMultipartFile excel() {
        return new MockMultipartFile(
                "file",
                "campi.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                conRighe(riga("Società", "Impianto", "Indirizzo"), riga("A", "B", "C")));
    }

    private static JwtRequestPostProcessor conRuolo(String ruolo) {
        return jwt().jwt(token -> token.claim("preferred_username", "mario"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + ruolo));
    }

    @Test
    void importaPerChiHaIlRuolo() throws Exception {
        when(service.importa(any(), eq(true)))
                .thenReturn(new EsitoImportazione(true, 1, 1, 0, 0, List.of(), 1, List.of()));

        mockMvc.perform(
                        multipart("/api/admin/importazione")
                                .file(excel())
                                .param("prova", "true")
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prova").value(true))
                .andExpect(jsonPath("$.inserite").value(1))
                .andExpect(jsonPath("$.daGeocodificare").value(1));
    }

    @Test
    void senzaLoginRisponde401() throws Exception {
        mockMvc.perform(multipart("/api/admin/importazione").file(excel()))
                .andExpect(status().isUnauthorized());

        verify(service, never()).importa(any(), anyBoolean());
    }

    @Test
    void unUtenteDiPresenzeSenzaIlRuoloRiceve403() throws Exception {
        mockMvc.perform(
                        multipart("/api/admin/importazione")
                                .file(excel())
                                .with(conRuolo("superadmin")))
                .andExpect(status().isForbidden());

        verify(service, never()).importa(any(), anyBoolean());
    }

    @Test
    void traduceUnFileNonValidoInUn400() throws Exception {
        when(service.importa(any(), eq(false)))
                .thenThrow(
                        new LettoreExcel.FileNonValidoException(
                                "Mancano le colonne obbligatorie: indirizzo"));

        mockMvc.perform(
                        multipart("/api/admin/importazione")
                                .file(excel())
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Mancano le colonne obbligatorie: indirizzo"));
    }

    @Test
    void lAnagraficaDiPresenzeSiImportaSoloConIlRuolo() throws Exception {
        when(sincronizzazione.sincronizza(true))
                .thenReturn(new EsitoImportazione(true, 3, 2, 1, 0, List.of(), 0, List.of()));

        mockMvc.perform(
                        post("/api/admin/anagrafica")
                                .param("prova", "true")
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserite").value(2));

        mockMvc.perform(post("/api/admin/anagrafica").with(conRuolo("superadmin")))
                .andExpect(status().isForbidden());
        verify(sincronizzazione, never()).sincronizza(false);
    }

    @Test
    void presenzeCheNonRispondeDiventaUn502() throws Exception {
        when(sincronizzazione.sincronizza(false))
                .thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(
                        post("/api/admin/anagrafica")
                                .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isBadGateway())
                .andExpect(
                        jsonPath("$.errore")
                                .value("L'anagrafica di presenze non risponde: riprova tra poco"));
    }
}
