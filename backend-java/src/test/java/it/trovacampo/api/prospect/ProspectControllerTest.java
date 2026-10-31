package it.trovacampo.api.prospect;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.config.ConfigurazioneSicurezza;
import it.trovacampo.api.indirizzi.Confidenza;
import it.trovacampo.api.indirizzi.FonteIndirizzo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** La lista delle ASD/SSD è commerciale: solo con il ruolo trovacampo-admin. */
@WebMvcTest(ProspectController.class)
@Import(ConfigurazioneSicurezza.class)
class ProspectControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ImportazioneProspect importazione;
    @MockitoBean private CollegamentoCampi collegamento;
    @MockitoBean private EsportazioneProspect esportazione;
    @MockitoBean private ProspectRepository repository;
    @MockitoBean private ArricchimentoProspect arricchimento;

    private static JwtRequestPostProcessor conRuolo(String ruolo) {
        return jwt().jwt(token -> token.claim("preferred_username", "mario"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + ruolo));
    }

    private static JwtRequestPostProcessor admin() {
        return conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE);
    }

    private static ImportazioneProspect.Stato fermo() {
        return new ImportazioneProspect.Stato(
                false, "mai", null, null, 0, false, null, null, false, null, null, null,
                ImportazioneProspect.Avanzamento.mai(), List.of());
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
                .avvia(eq(new ImportazioneProspect.Opzioni(List.of(12), 2, false, AmbitoDettagli.CALCIO)), anyString());
    }

    @Test
    void iDettagliSiScelgonoPerAmbito() throws Exception {
        when(importazione.avvia(any(), anyString())).thenReturn(true);
        when(importazione.stato()).thenReturn(fermo());

        mockMvc.perform(post("/api/admin/prospect/importazione").param("dettagli", "figc").with(admin()))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/prospect/importazione").param("dettagli", "tutte").with(admin()))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/prospect/importazione").param("dettagli", "boh").with(admin()))
                .andExpect(status().isBadRequest());

        verify(importazione).avvia(eq(new ImportazioneProspect.Opzioni(List.of(), null, false, AmbitoDettagli.FIGC)), anyString());
        verify(importazione).avvia(eq(new ImportazioneProspect.Opzioni(List.of(), null, false, AmbitoDettagli.TUTTE)), anyString());
    }

    @Test
    void riprendiRipartedallUltimoGiroOppure404() throws Exception {
        when(importazione.stato()).thenReturn(fermo());
        when(importazione.riprendi(anyString())).thenReturn(java.util.Optional.of(true));
        mockMvc.perform(post("/api/admin/prospect/importazione/riprendi").with(admin()))
                .andExpect(status().isAccepted());

        when(importazione.riprendi(anyString())).thenReturn(java.util.Optional.empty());
        mockMvc.perform(post("/api/admin/prospect/importazione/riprendi").with(admin()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/prospect/importazione/riprendi")).andExpect(status().isUnauthorized());
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
        // Il file si scrive in streaming, su un altro thread: si aspetta la fine
        // prima di guardare la risposta, altrimenti i filtri di sicurezza e il
        // thread dello streaming scrivono le intestazioni insieme (e in CI capita
        // una ConcurrentModificationException).
        MvcResult avviata =
                mockMvc.perform(get("/api/admin/prospect/export.xlsx").with(admin()))
                        .andExpect(request().asyncStarted())
                        .andReturn();
        mockMvc.perform(asyncDispatch(avviata))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                "Content-Disposition", "attachment; filename=\"prospect_italia.xlsx\""));
    }

    @Test
    void laGeocodificaSenzaProspectLoDiceENonFallisce() throws Exception {
        when(arricchimento.avvia(any()))
                .thenReturn(new ArricchimentoProspect.Avvio(
                        false, ArricchimentoProspect.NESSUN_PROSPECT, ArricchimentoProspect.Stato.MAI_PARTITO));

        mockMvc.perform(post("/api/admin/prospect/geocodifica")
                        .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avviato").value(false))
                .andExpect(jsonPath("$.messaggio").value(ArricchimentoProspect.NESSUN_PROSPECT));
    }

    @Test
    void laGeocodificaParteInSottofondo() throws Exception {
        when(arricchimento.avvia(new ArricchimentoProspect.Richiesta(false, 20)))
                .thenReturn(new ArricchimentoProspect.Avvio(
                        true, "Ricerca avviata su 20 società",
                        new ArricchimentoProspect.Stato(true, null, null, 20, 0, 0, 0, "In corso")));

        mockMvc.perform(post("/api/admin/prospect/geocodifica?limite=20")
                        .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.stato.daCercare").value(20));
    }

    @Test
    void laCoperturaPerFonteEConfidenza() throws Exception {
        when(collegamento.copertura())
                .thenReturn(new CollegamentoCampi.Esito(
                        10, 7, 6, Map.of(FonteIndirizzo.CAMPO_TROVACAMPO, 5, FonteIndirizzo.OPEN_DATA, 2),
                        Map.of(), Map.of(Confidenza.ALTA, 5), true));

        mockMvc.perform(get("/api/admin/prospect/copertura")
                        .with(conRuolo(ConfigurazioneSicurezza.RUOLO_AMMINISTRATORE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collegati").value(7))
                .andExpect(jsonPath("$.copertura").value(70.0))
                .andExpect(jsonPath("$.perFonte.OPEN_DATA").value(2));
        mockMvc.perform(get("/api/admin/prospect/copertura")).andExpect(status().isUnauthorized());
    }
}
