package it.trovacampo.api.importazione;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Senza token configurato l'endpoint deve fingere di non esistere. */
@WebMvcTest(ImportazioneController.class)
class ImportazioneControllerSenzaTokenTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ImportazioneService service;

    @Test
    void lEndpointNonEsiste() throws Exception {
        mockMvc.perform(
                        multipart("/api/admin/importazione")
                                .file(ImportazioneControllerTest.excel())
                                .header(ImportazioneController.INTESTAZIONE, ""))
                .andExpect(status().isNotFound());

        verify(service, never()).importa(any(), any(Boolean.class));
    }
}
