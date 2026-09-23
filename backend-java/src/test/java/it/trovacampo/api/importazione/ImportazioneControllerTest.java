package it.trovacampo.api.importazione;

import static it.trovacampo.api.importazione.FileExcel.conRighe;
import static it.trovacampo.api.importazione.FileExcel.riga;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImportazioneController.class)
@TestPropertySource(properties = "trovacampo.importazione.token=" + ImportazioneControllerTest.TOKEN)
class ImportazioneControllerTest {

    static final String TOKEN = "segreto-di-prova";

    static MockMultipartFile excel() {
        return new MockMultipartFile(
                "file",
                "campi.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                conRighe(riga("Società", "Impianto", "Indirizzo"), riga("A", "B", "C")));
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ImportazioneService service;

    @Test
    void importaERestituisceLEsito() throws Exception {
        when(service.importa(any(), eq(true)))
                .thenReturn(new EsitoImportazione(true, 1, 1, 0, 0, List.of(), 1, List.of()));

        mockMvc.perform(
                        multipart("/api/admin/importazione")
                                .file(excel())
                                .param("prova", "true")
                                .header(ImportazioneController.INTESTAZIONE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prova").value(true))
                .andExpect(jsonPath("$.inserite").value(1))
                .andExpect(jsonPath("$.daGeocodificare").value(1));
    }

    @Test
    void rifiutaUnTokenSbagliato() throws Exception {
        mockMvc.perform(
                        multipart("/api/admin/importazione")
                                .file(excel())
                                .header(ImportazioneController.INTESTAZIONE, "indovinato"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errore").value("Token di importazione mancante o errato"));

        verify(service, never()).importa(any(), any(Boolean.class));
    }

    @Test
    void rifiutaUnaRichiestaSenzaToken() throws Exception {
        mockMvc.perform(multipart("/api/admin/importazione").file(excel()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void traduceUnFileNonValidoInUn400() throws Exception {
        when(service.importa(any(), eq(false)))
                .thenThrow(new LettoreExcel.FileNonValidoException("Mancano le colonne obbligatorie: indirizzo"));

        mockMvc.perform(
                        multipart("/api/admin/importazione")
                                .file(excel())
                                .header(ImportazioneController.INTESTAZIONE, TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Mancano le colonne obbligatorie: indirizzo"));
    }
}
