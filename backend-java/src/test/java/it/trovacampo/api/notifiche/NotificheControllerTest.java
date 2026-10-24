package it.trovacampo.api.notifiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.trovacampo.api.anagrafica.AnagraficaPresenze.Partita;
import it.trovacampo.api.anagrafica.PartiteSuiCampi;
import it.trovacampo.api.config.ConfigurazioneSicurezza;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gli endpoint delle notifiche con la sicurezza vera (devono restare
 * pubblici), la validazione vera e il servizio vero: finto solo Mongo.
 */
@WebMvcTest(NotificheController.class)
@Import({ConfigurazioneSicurezza.class, IscrizioniService.class, ProssimePartite.class, PartiteDaAvvisare.class})
class NotificheControllerTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc:def-123";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IscrizioniRepository repository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoTemplate mongo;

    @MockitoBean private DepositoChiaviVapid deposito;

    @MockitoBean private PartiteSuiCampi partite;

    @MockitoBean private SocietaRepository campi;

    @MockitoBean private RegistroInvii registro;

    private final Browser browser = new Browser();

    @BeforeEach
    void prepara() {
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));
    }

    private String iscrizione(String endpoint, String squadre, String extra) {
        return """
                {"endpoint": "%s",
                 "keys": {"p256dh": "%s", "auth": "%s"},
                 "squadre": [%s]%s}"""
                .formatted(endpoint, browser.p256dh(), browser.auth(), squadre, extra);
    }

    private Iscrizione salvata() {
        ArgumentCaptor<Iscrizione> salvata = ArgumentCaptor.forClass(Iscrizione.class);
        verify(repository).save(salvata.capture());
        return salvata.getValue();
    }

    @Test
    void laChiavePubblicaEPerTutti() throws Exception {
        ChiaviVapid chiavi = ChiaviVapid.nuove();
        when(deposito.chiavi()).thenReturn(chiavi);

        mockMvc.perform(get("/api/notifiche/chiave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chiave").value(chiavi.pubblicaBase64()))
                .andExpect(jsonPath("$.privata").doesNotExist());
    }

    @Test
    void siIscriveSenzaLoginConLeSquadreSeguite() throws Exception {
        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        iscrizione(
                                                ENDPOINT,
                                                "\"412|eccellenza|regionali|\", \"412|eccellenza|regionali|\"",
                                                ", \"avvisoSquadre\": true")))
                .andExpect(status().isNoContent());

        Iscrizione salvata = salvata();
        assertThat(salvata.id()).isEqualTo(Iscrizione.idDi(ENDPOINT));
        assertThat(salvata.squadre()).containsExactly("412|eccellenza|regionali|");
        assertThat(salvata.avvisoSquadre()).isTrue();
        assertThat(salvata.avvisoVicino()).isFalse();
        assertThat(salvata.lat()).isNull();
    }

    @Test
    void conLeVicineSalvaLaPosizioneArrotondataEIlRaggio() throws Exception {
        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        iscrizione(
                                                ENDPOINT, "",
                                                ", \"avvisoVicino\": true,"
                                                        + " \"posizione\": {\"lat\": 41.902345, \"lng\": 12.496789}")))
                .andExpect(status().isNoContent());

        Iscrizione salvata = salvata();
        assertThat(salvata.lat()).isEqualTo(41.902);
        assertThat(salvata.lng()).isEqualTo(12.497);
        assertThat(salvata.raggioKm()).isEqualTo(10);
        assertThat(salvata.posizioneAggiornataIl()).isNotNull();
    }

    @Test
    void senzaLAvvisoVicinoLaPosizioneNonSiTiene() throws Exception {
        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        iscrizione(
                                                ENDPOINT, "",
                                                ", \"avvisoSquadre\": true, \"raggioKm\": 20,"
                                                        + " \"posizione\": {\"lat\": 41.9, \"lng\": 12.5}")))
                .andExpect(status().isNoContent());

        assertThat(salvata().lat()).isNull();
        assertThat(salvata().raggioKm()).isNull();
    }

    @Test
    void leVicineSenzaPosizioneNo() throws Exception {
        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(iscrizione(ENDPOINT, "", ", \"avvisoVicino\": true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Per le partite vicine serve la posizione"));
        verify(repository, never()).save(any());
    }

    @Test
    void soloVersoIServiziPushConosciuti() throws Exception {
        for (String endpoint :
                List.of(
                        "https://esempio.it/push",
                        "http://fcm.googleapis.com/fcm/send/abc",
                        "https://fcm.googleapis.com.esempio.it/fcm/send/abc",
                        "https://fcm.googleapis.com:8443/fcm/send/abc",
                        "https://utente@fcm.googleapis.com/fcm/send/abc",
                        "https://presenze-backend/api")) {
            mockMvc.perform(
                            put("/api/notifiche/iscrizione")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(iscrizione(endpoint, "", "")))
                    .andExpect(status().isBadRequest());
        }
        // Un sottodominio di un servizio conosciuto sì: Apple e Mozilla ne usano diversi.
        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(iscrizione("https://web.push.apple.com/QGx", "", "")))
                .andExpect(status().isNoContent());
    }

    @Test
    void chiaviDelBrowserEPreferenzeSiControllano() throws Exception {
        String chiaveFuoriCurva =
                """
                {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}, "squadre": []}"""
                        .formatted(ENDPOINT, "B" + "A".repeat(86), browser.auth());
        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(chiaveFuoriCurva))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Chiave p256dh non valida"));

        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(iscrizione(ENDPOINT, "\"<script>\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Squadra seguita non valida"));

        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        iscrizione(
                                                ENDPOINT, "",
                                                ", \"avvisoVicino\": true, \"raggioKm\": 51,"
                                                        + " \"posizione\": {\"lat\": 41.9, \"lng\": 12.5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Raggio di al massimo 50 km"));

        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        iscrizione(
                                                ENDPOINT, "",
                                                ", \"avvisoVicino\": true,"
                                                        + " \"posizione\": {\"lat\": 91, \"lng\": 12.5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Latitudine non valida"));

        String trentunoSquadre =
                String.join(
                        ",",
                        java.util.stream.IntStream.rangeClosed(1, 31)
                                .mapToObj(i -> "\"" + i + "|eccellenza|regionali|\"")
                                .toList());
        mockMvc.perform(
                        put("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(iscrizione(ENDPOINT, trentunoSquadre, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Al massimo 30 squadre seguite"));

        verify(repository, never()).save(any());
    }

    @Test
    void unaRichiestaTroppoGrandeSiFermaPrima() throws Exception {
        String enorme = iscrizione(ENDPOINT, "", ", \"riempitivo\": \"" + "x".repeat(20_000) + "\"");

        mockMvc.perform(put("/api/notifiche/iscrizione").contentType(MediaType.APPLICATION_JSON).content(enorme))
                .andExpect(status().isPayloadTooLarge());
        verify(repository, never()).save(any());
    }

    @Test
    void siCancellaConLEndpoint() throws Exception {
        mockMvc.perform(
                        delete("/api/notifiche/iscrizione")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"endpoint\": \"" + ENDPOINT + "\"}"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById(Iscrizione.idDi(ENDPOINT));
    }

    /** Domani alle 15:00 (all'incirca: basta che sia nella settimana), sul campo della Lodigiani. */
    private void unaPartitaDomani() {
        OffsetDateTime domani = OffsetDateTime.now().plusDays(1).truncatedTo(ChronoUnit.HOURS);
        when(partite.fra(any(), any()))
                .thenReturn(
                        List.of(
                                new Partita(
                                        9L, domani, "DA_GIOCARE", 190L, "LODIGIANI CALCIO 1972", "VIS SEZZE",
                                        "ECCELLENZA", "Regionali", "B", 3, 412L, 77L, "", "B")));
        when(campi.findByAnagraficaImpiantoIdIn(any()))
                .thenReturn(
                        List.of(
                                new Societa()
                                        .setId("campo-lodigiani")
                                        .setNomeImpianto("La Borghesiana")
                                        .setAnagraficaSocietaId(412L)
                                        .setAnagraficaImpiantoId(190L)
                                        .setLat(41.9)
                                        .setLng(12.5)));
        when(registro.giaPrenotati(any())).thenReturn(Set.of());
    }

    @Test
    void leProssimePartiteSenzaLoginEConLeScelteDelBrowser() throws Exception {
        unaPartitaDomani();

        mockMvc.perform(
                        post("/api/notifiche/prossime")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"squadre": ["412|eccellenza|regionali|"],
                                         "posizione": {"lat": 41.91, "lng": 12.5}, "raggioKm": 5,
                                         "endpoint": "%s"}"""
                                                .formatted(ENDPOINT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partite[0].casa").value("LODIGIANI CALCIO 1972"))
                .andExpect(jsonPath("$.partite[0].campo.id").value("campo-lodigiani"))
                .andExpect(jsonPath("$.partite[0].avvisi[0].tipo").value("squadra"))
                .andExpect(jsonPath("$.partite[0].avvisi[0].seguite[0]").value("LODIGIANI CALCIO 1972"))
                .andExpect(jsonPath("$.partite[0].avvisi[0].stato").value("previsto"))
                .andExpect(jsonPath("$.partite[0].avvisi[1].tipo").value("vicino"))
                .andExpect(jsonPath("$.partite[0].avvisi[1].distanzaKm").value(1.1))
                .andExpect(jsonPath("$.altre").value(false));

        // Non salva niente: le scelte servono a questa risposta e basta.
        verify(repository, never()).save(any());
        verify(registro, never()).prenota(any());
    }

    @Test
    void leScelteDelleProssimeSiControllanoComeLIscrizione() throws Exception {
        mockMvc.perform(
                        post("/api/notifiche/prossime")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"squadre\": [\"<script>\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Squadra seguita non valida"));
        mockMvc.perform(
                        post("/api/notifiche/prossime")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"squadre\": [], \"raggioKm\": 51,"
                                                + " \"posizione\": {\"lat\": 41.9, \"lng\": 12.5}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errore").value("Raggio di al massimo 50 km"));
        mockMvc.perform(
                        post("/api/notifiche/prossime")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"squadre\": [], \"riempitivo\": \"" + "x".repeat(20_000) + "\"}"))
                .andExpect(status().isPayloadTooLarge());
        verify(partite, never()).fra(any(), any());
    }
}
