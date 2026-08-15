package it.trovacampo.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.dominio.Campionato;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.dominio.TipoCampionato;
import it.trovacampo.api.service.SocietaService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SocietaController.class)
class SocietaControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SocietaService service;

    private Societa certosa() {
        return new Societa()
                .setId("1")
                .setSiglaSocieta("A.S.D.")
                .setNomeSocieta("Certosa Calcio")
                .setNomeImpianto("Campo Certosa")
                .setIndirizzoImpianto("Via della Certosa 12")
                .setLat(41.8919)
                .setLng(12.4863)
                .setPresidente("Mario Rossi")
                .setCampionati(
                        List.of(
                                new Campionato(
                                        "Terza Categoria",
                                        "B",
                                        "LAZIO",
                                        TipoCampionato.AGONISTICA)))
                .setTestoRicerca("certosa calcio");
    }

    @Test
    void restituisceIRisultatiDellaRicerca() throws Exception {
        when(service.cerca("certosa")).thenReturn(List.of(certosa()));

        mockMvc.perform(get("/api/societa").param("nome", "certosa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomeSocieta").value("Certosa Calcio"))
                .andExpect(jsonPath("$[0].lat").value(41.8919));
    }

    @Test
    void nonEsponeIlTestoDiRicercaInterno() throws Exception {
        when(service.cerca("certosa")).thenReturn(List.of(certosa()));

        mockMvc.perform(get("/api/societa").param("nome", "certosa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testoRicerca").doesNotExist());
    }

    @Test
    void ometteICampiFacoltativiNonValorizzati() throws Exception {
        when(service.cerca("tor"))
                .thenReturn(List.of(new Societa().setId("2").setNomeSocieta("Tor di Pippo")));

        mockMvc.perform(get("/api/societa").param("nome", "tor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lat").doesNotExist())
                .andExpect(jsonPath("$[0].campionati").doesNotExist());
    }

    @Test
    void restituisceAnagraficaECampionatiDellaSocieta() throws Exception {
        when(service.perId("1")).thenReturn(Optional.of(certosa()));

        mockMvc.perform(get("/api/societa/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presidente").value("Mario Rossi"))
                .andExpect(jsonPath("$.campionati[0].descrizione").value("Terza Categoria"))
                .andExpect(jsonPath("$.campionati[0].tipo").value("Agonistica"));
    }

    @Test
    void rispondeNotFoundSeLaSocietaNonEsiste() throws Exception {
        when(service.perId("999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/societa/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errore").value("Società non trovata"));
    }

    @Test
    void creaIlCampoInviato() throws Exception {
        when(service.inserisci(any())).thenReturn(certosa());

        mockMvc.perform(
                        post("/api/societa")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "nomeSocieta": "Certosa Calcio",
                                          "nomeImpianto": "Campo Certosa",
                                          "indirizzoImpianto": "Via della Certosa 12"
                                        }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void rifiutaIlCampoSenzaIDatiObbligatori() throws Exception {
        mockMvc.perform(
                        post("/api/societa")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nomeSocieta\": \"Certosa Calcio\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").exists());
    }
}
