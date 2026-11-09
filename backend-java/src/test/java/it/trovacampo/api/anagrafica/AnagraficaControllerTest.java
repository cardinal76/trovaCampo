package it.trovacampo.api.anagrafica;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.sicurezza.ConfigurazioneSicurezza;
import it.trovacampo.api.societa.Campionato;
import it.trovacampo.api.societa.Societa;
import it.trovacampo.api.societa.SocietaService;
import it.trovacampo.api.societa.TipoCampionato;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

/** Con la sicurezza vera: squadre, partite e risultati devono restare pubblici. */
@WebMvcTest(AnagraficaController.class)
@Import(ConfigurazioneSicurezza.class)
class AnagraficaControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SocietaService service;

    @MockitoBean private SquadreSocieta squadre;

    @MockitoBean private PartiteSuiCampi partite;

    @MockitoBean private RisultatiSocieta risultati;

    @MockitoBean private AccessoSocieta accesso;

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
    void leSquadreDellaSocietaSonoPubbliche() throws Exception {
        Societa certosa = certosa().setAnagraficaSocietaId(42L);
        when(service.perId("1")).thenReturn(Optional.of(certosa));
        when(squadre.di(certosa))
                .thenReturn(
                        List.of(
                                new SquadreSocieta.Squadra(
                                        "ECCELLENZA", "Regionali", "2026/2027", "A", "", false,
                                        "Campo Certosa", "42|eccellenza|regionali|")));

        mockMvc.perform(get("/api/societa/1/squadre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].campionato").value("ECCELLENZA"))
                .andExpect(jsonPath("$[0].chiave").value("42|eccellenza|regionali|"))
                .andExpect(jsonPath("$[0].girone").value("A"));
    }

    @Test
    void leSquadreDiconoAQualeComunicatoSonoAggiornatiIRisultati() throws Exception {
        Societa certosa = certosa().setAnagraficaSocietaId(42L);
        when(service.perId("1")).thenReturn(Optional.of(certosa));
        when(squadre.di(certosa))
                .thenReturn(
                        List.of(
                                new SquadreSocieta.Squadra(
                                        "ECCELLENZA", "Regionali", "2026/2027", "A", "", false,
                                        "Campo Certosa", "42|eccellenza|regionali|",
                                        new it.trovacampo.api.anagrafica.AnagraficaPresenze
                                                .RisultatiDaComunicato(
                                                55,
                                                java.time.LocalDate.of(2026, 9, 24),
                                                "Regionali",
                                                java.time.Instant.parse("2026-09-26T11:00:00Z")))));

        mockMvc.perform(get("/api/societa/1/squadre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].risultatiDaComunicato.numero").value(55))
                .andExpect(jsonPath("$[0].risultatiDaComunicato.data").value("2026-09-24"))
                .andExpect(
                        jsonPath("$[0].risultatiDaComunicato.portaleNonDisponibileDal")
                                .value("2026-09-26T11:00:00Z"));
    }

    @Test
    void l_accessoDiUnClubClienteEPubblicoESoloAliasEStemma() throws Exception {
        Societa certosa = certosa().setAnagraficaSocietaId(42L);
        when(service.perId("1")).thenReturn(Optional.of(certosa));
        when(accesso.di(certosa))
                .thenReturn(
                        Optional.of(
                                new AnagraficaPresenze.Accesso(
                                        "certosa", "/api/organizzazioni/3/logo")));

        mockMvc.perform(get("/api/societa/1/accesso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("certosa"))
                .andExpect(jsonPath("$.stemma").value("/api/organizzazioni/3/logo"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void senzaAccessoLaRispostaEVuota() throws Exception {
        when(service.perId("1")).thenReturn(Optional.of(certosa()));
        when(accesso.di(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/societa/1/accesso")).andExpect(status().isNoContent());
    }

    @Test
    void l_accessoDiUnaSocietaCheNonCeRisponde404() throws Exception {
        when(service.perId("9")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/societa/9/accesso")).andExpect(status().isNotFound());
    }

    @Test
    void leSquadreDiUnaSocietaCheNonCeRispondono404() throws Exception {
        when(service.perId("9")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/societa/9/squadre")).andExpect(status().isNotFound());
    }

    @Test
    void senzaPresenzeLeSquadreRispondono502() throws Exception {
        when(service.perId("1")).thenReturn(Optional.of(certosa()));
        when(squadre.di(any())).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/api/societa/1/squadre")).andExpect(status().isBadGateway());
    }

    private static PartiteSuiCampi.Partita boreale() {
        return new PartiteSuiCampi.Partita(
                OffsetDateTime.of(2026, 9, 6, 11, 0, 0, 0, ZoneOffset.ofHours(2)),
                "BOREALE",
                "VIGOR PERCONTI",
                7L,
                "ECCELLENZA",
                "Regionali",
                31L,
                "A",
                1,
                "https://www.lnd.it/stemmi/boreale.png",
                null,
                null,
                null);
    }

    @Test
    void lePartiteSulCampoSonoPubbliche() throws Exception {
        Societa certosa = certosa().setAnagraficaImpiantoId(190L);
        when(service.perId("1")).thenReturn(Optional.of(certosa));
        when(partite.sulCampo(certosa)).thenReturn(List.of(boreale()));

        mockMvc.perform(get("/api/societa/1/partite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].casa").value("BOREALE"))
                .andExpect(jsonPath("$[0].dataOra").value("2026-09-06T11:00:00+02:00"))
                .andExpect(jsonPath("$[0].girone").value("A"));
        mockMvc.perform(get("/api/societa/9/partite")).andExpect(status().isNotFound());
    }

    @Test
    void lePartiteDellaMappaSonoPubblicheEPerCampo() throws Exception {
        when(partite.perLaMappa()).thenReturn(Map.of(190L, List.of(boreale())));

        mockMvc.perform(get("/api/campi/partite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['190'][0].ospite").value("VIGOR PERCONTI"));
    }

    /** I soli punteggi in diretta, pubblici: una partita assente non è in diretta. */
    @Test
    void iPunteggiInDirettaSonoPubblici() throws Exception {
        when(partite.live(List.of(5L, 6L)))
                .thenReturn(Map.of(5L, new AnagraficaPresenze.Live(2, 1)));

        mockMvc.perform(get("/api/partite/live").param("ids", "5,6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['5'].golCasa").value(2))
                .andExpect(jsonPath("$['6']").doesNotExist());
    }

    @Test
    void piuDiCentoPartiteInDirettaNo() throws Exception {
        String troppe =
                java.util.stream.LongStream.rangeClosed(1, 101)
                        .mapToObj(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(get("/api/partite/live").param("ids", troppe))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gliUltimiRisultatiSonoPubbliciEPortanoLoStato() throws Exception {
        when(service.perId("1")).thenReturn(Optional.of(certosa()));
        when(risultati.di(any()))
                .thenReturn(
                        new RisultatiSocieta.Risultati(
                                RisultatiSocieta.Stato.DISPONIBILI,
                                List.of(
                                        new RisultatiSocieta.Risultato(
                                                50L,
                                                OffsetDateTime.of(
                                                        2026, 10, 11, 10, 30, 0, 0, ZoneOffset.ofHours(2)),
                                                12L,
                                                "UNDER 17 PROVINCIALE",
                                                "Roma",
                                                41L,
                                                "A",
                                                3,
                                                "",
                                                "A.S.D. ESEMPIO",
                                                "ALTRA A.S.D.",
                                                2,
                                                1,
                                                true,
                                                "VITTORIA"))));

        mockMvc.perform(get("/api/societa/1/risultati"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("DISPONIBILI"))
                .andExpect(jsonPath("$.risultati[0].data").value("2026-10-11T10:30:00+02:00"))
                .andExpect(jsonPath("$.risultati[0].esito").value("VITTORIA"))
                .andExpect(jsonPath("$.risultati[0].golCasa").value(2))
                .andExpect(jsonPath("$.risultati[0].inCasa").value(true));
        mockMvc.perform(get("/api/societa/9/risultati")).andExpect(status().isNotFound());
    }

    /**
     * Presenze non risponde, ma la scheda dei risultati non e' la pagina: 200
     * con lo stato, cosi' l'app lo racconta a parole invece di mostrare un
     * errore al posto di tutto.
     */
    @Test
    void senzaPresenzeIRisultatiRestano200ConLoStato() throws Exception {
        when(service.perId("1")).thenReturn(Optional.of(certosa()));
        when(risultati.di(any()))
                .thenReturn(
                        new RisultatiSocieta.Risultati(RisultatiSocieta.Stato.ERRORE, List.of()));

        mockMvc.perform(get("/api/societa/1/risultati"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stato").value("ERRORE"))
                .andExpect(jsonPath("$.risultati").isEmpty());
    }
}
