package it.trovacampo.api.notifiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.trovacampo.api.anagrafica.AnagraficaPresenze.Partita;
import it.trovacampo.api.anagrafica.PartiteSuiCampi;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Il giro delle notifiche con le partite, le iscrizioni e il servizio push
 * finti: chi riceve cosa, e quando.
 */
class PianificatoreNotificheTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    /** Domenica 6 settembre 2026, alle 14:00 di Roma. */
    private static final LocalDateTime ORE_14 = LocalDateTime.of(2026, 9, 6, 14, 0);

    private static final long LODIGIANI = 412L;
    private static final long VIS_SEZZE = 77L;
    private static final long IMPIANTO = 190L;
    /** Il campo della Lodigiani, in TrovaCampo. */
    private static final double LAT_CAMPO = 41.9000;
    private static final double LNG_CAMPO = 12.5000;

    private final IscrizioniRepository iscrizioni = mock(IscrizioniRepository.class);
    private final PartiteSuiCampi partite = mock(PartiteSuiCampi.class);
    private final SocietaRepository campi = mock(SocietaRepository.class);
    private final RegistroInMemoria registro = new RegistroInMemoria();
    private final InvioWebPush invio = mock(InvioWebPush.class);
    private final ObjectMapper json = new ObjectMapper();
    private final OrologioAMano orologio = new OrologioAMano(ORE_14.atZone(ROMA).toInstant());

    private final List<Partita> calendario = new ArrayList<>();
    private final List<Iscrizione> iscritte = new ArrayList<>();

    /** Come quello in Mongo, ma in memoria: basta a vedere i doppioni. */
    private static final class RegistroInMemoria implements RegistroInvii {
        final Set<String> prenotati = new HashSet<>();

        @Override
        public boolean prenota(String chiave) {
            return prenotati.add(chiave);
        }

        @Override
        public void annulla(String chiave) {
            prenotati.remove(chiave);
        }
    }

    private static final class OrologioAMano extends Clock {
        private Instant adesso;

        OrologioAMano(Instant adesso) {
            this.adesso = adesso;
        }

        void avanti(Duration quanto) {
            adesso = adesso.plus(quanto);
        }

        @Override
        public ZoneId getZone() {
            return ROMA;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return adesso;
        }
    }

    @BeforeEach
    void prepara() {
        when(iscrizioni.conAvvisiAccesi()).thenAnswer(invocazione -> List.copyOf(iscritte));
        // Come la cache vera: dal calendario, solo quelle nell'intervallo chiesto.
        when(partite.fra(any(), any()))
                .thenAnswer(
                        invocazione -> {
                            Instant da = invocazione.getArgument(0);
                            Instant a = invocazione.getArgument(1);
                            return calendario.stream()
                                    .filter(p -> !p.dataOra().toInstant().isBefore(da))
                                    .filter(p -> p.dataOra().toInstant().isBefore(a))
                                    .toList();
                        });
        when(campi.findByAnagraficaImpiantoIdIn(any()))
                .thenReturn(
                        List.of(
                                new Societa()
                                        .setId("campo-lodigiani")
                                        .setNomeImpianto("La Borghesiana \"C\"")
                                        .setAnagraficaSocietaId(LODIGIANI)
                                        .setAnagraficaImpiantoId(IMPIANTO)
                                        .setLat(LAT_CAMPO)
                                        .setLng(LNG_CAMPO)));
        when(invio.invia(any(), anyString(), any())).thenReturn(InvioWebPush.Esito.CONSEGNATA);
    }

    private PianificatoreNotifiche pianificatore() {
        return new PianificatoreNotifiche(iscrizioni, partite, campi, registro, invio, json, orologio);
    }

    /** LODIGIANI (prima squadra) – VIS SEZZE (seconda, "B"), in Eccellenza sul campo della Lodigiani. */
    private Partita partitaAlle(int ora, int minuti) {
        return partitaAlle(ora, minuti, "");
    }

    private Partita partitaAlle(int ora, int minuti, String squadraCasa) {
        Partita partita =
                new Partita(
                        (long) (ora * 100 + minuti),
                        ORE_14.withHour(ora).withMinute(minuti).atZone(ROMA).toOffsetDateTime()
                                .withOffsetSameInstant(ZoneOffset.UTC),
                        "DA_GIOCARE",
                        IMPIANTO,
                        "LODIGIANI CALCIO 1972",
                        "VIS SEZZE",
                        "ECCELLENZA",
                        "Regionali",
                        "B",
                        3,
                        LODIGIANI,
                        VIS_SEZZE,
                        squadraCasa,
                        "B");
        calendario.add(partita);
        return partita;
    }

    private Iscrizione segue(String nome, String... squadre) {
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + nome;
        Iscrizione iscrizione =
                new Iscrizione(
                        Iscrizione.idDi(endpoint), endpoint, "p256dh", "auth", List.of(squadre), true, false,
                        null, null, null, null, Instant.now());
        iscritte.add(iscrizione);
        return iscrizione;
    }

    private Iscrizione vicinoA(String nome, double lat, double lng, int raggioKm) {
        String endpoint = "https://fcm.googleapis.com/fcm/send/" + nome;
        Iscrizione iscrizione =
                new Iscrizione(
                        Iscrizione.idDi(endpoint), endpoint, "p256dh", "auth", List.of(), false, true,
                        lat, lng, raggioKm, Instant.now(), Instant.now());
        iscritte.add(iscrizione);
        return iscrizione;
    }

    private List<JsonNode> mandateA(Iscrizione iscrizione) throws Exception {
        ArgumentCaptor<String> contenuti = ArgumentCaptor.forClass(String.class);
        verify(invio, atLeast(0)).invia(eq(iscrizione), contenuti.capture(), any());
        List<JsonNode> lette = new ArrayList<>();
        for (String contenuto : contenuti.getAllValues()) {
            lette.add(json.readTree(contenuto));
        }
        return lette;
    }

    @Test
    void unOraPrimaDellaPartitaDiUnaSquadraSeguita() throws Exception {
        partitaAlle(15, 0);
        Iscrizione tifoso = segue("tifoso", "412|eccellenza|regionali|");

        PianificatoreNotifiche.Resoconto resoconto = pianificatore().giro();

        assertThat(resoconto.mandate()).isEqualTo(1);
        List<JsonNode> mandate = mandateA(tifoso);
        assertThat(mandate).hasSize(1);
        JsonNode notifica = mandate.getFirst();
        assertThat(notifica.get("titolo").asText()).isEqualTo("Tra un'ora: LODIGIANI CALCIO 1972 – VIS SEZZE");
        assertThat(notifica.get("testo").asText())
                .isEqualTo("Alle 15:00 · ECCELLENZA girone B · La Borghesiana \"C\"");
        assertThat(notifica.get("url").asText()).isEqualTo("/societa/campo-lodigiani");
        verify(invio).invia(eq(tifoso), anyString(), eq(Duration.ofMinutes(60)));
    }

    @Test
    void anchePerLaSquadraOspite() throws Exception {
        partitaAlle(15, 0);
        Iscrizione tifoso = segue("tifoso", "77|eccellenza|regionali|b");

        pianificatore().giro();

        assertThat(mandateA(tifoso)).hasSize(1);
    }

    @Test
    void laSecondaSquadraNonEQuellaSeguita() throws Exception {
        // La VIS SEZZE che gioca è la "B": chi segue la prima non riceve niente.
        partitaAlle(15, 0);
        Iscrizione tifoso = segue("tifoso", "77|eccellenza|regionali|");

        pianificatore().giro();

        assertThat(mandateA(tifoso)).isEmpty();
    }

    @Test
    void daUnPresenzeSenzaLetteraVaLaSocietaNelCampionato() throws Exception {
        partitaAlle(15, 0, null);
        Iscrizione tifoso = segue("tifoso", "412|eccellenza|regionali|b");

        pianificatore().giro();

        assertThat(mandateA(tifoso)).hasSize(1);
    }

    @Test
    void unAltroCampionatoDellaStessaSocietaNo() throws Exception {
        partitaAlle(15, 0);
        Iscrizione tifoso = segue("tifoso", "412|juniores under 19 regionale|regionali|");

        pianificatore().giro();

        assertThat(mandateA(tifoso)).isEmpty();
    }

    @Test
    void conLAvvisoSquadreSpentoNiente() throws Exception {
        partitaAlle(15, 0);
        String endpoint = "https://fcm.googleapis.com/fcm/send/spento";
        Iscrizione spento =
                new Iscrizione(
                        Iscrizione.idDi(endpoint), endpoint, "p256dh", "auth",
                        List.of("412|eccellenza|regionali|"), false, true, 45.0, 9.0, 10, Instant.now(),
                        Instant.now());
        iscritte.add(spento);

        pianificatore().giro();

        assertThat(mandateA(spento)).isEmpty();
    }

    @Test
    void laFinestraDelleSquadreVaDa55A65Minuti() throws Exception {
        partitaAlle(14, 54); // 54': troppo vicina
        partitaAlle(14, 55); // 55': dentro
        partitaAlle(15, 4); // 64': dentro
        partitaAlle(15, 5); // 65': ancora presto, la prende il giro dopo
        Iscrizione tifoso = segue("tifoso", "412|eccellenza|regionali|");

        pianificatore().giro();

        assertThat(mandateA(tifoso))
                .extracting(notifica -> notifica.get("testo").asText().substring(0, 10))
                .containsExactlyInAnyOrder("Alle 14:55", "Alle 15:04");
    }

    @Test
    void maiDueVolteLaStessaNotifica() throws Exception {
        partitaAlle(15, 0);
        Iscrizione tifoso = segue("tifoso", "412|eccellenza|regionali|");

        pianificatore().giro();
        // Cinque minuti dopo la partita è ancora nella finestra (55'),
        orologio.avanti(Duration.ofMinutes(5));
        PianificatoreNotifiche.Resoconto secondo = pianificatore().giro();
        // e dopo un riavvio anche: il registro non è nel pianificatore.
        PianificatoreNotifiche.Resoconto dopoIlRiavvio = pianificatore().giro();

        assertThat(secondo.mandate()).isZero();
        assertThat(dopoIlRiavvio.mandate()).isZero();
        verify(invio, times(1)).invia(eq(tifoso), anyString(), any());
    }

    @Test
    void unaPartitaSpostataSiRiannuncia() throws Exception {
        Partita alle15 = partitaAlle(15, 0);
        Iscrizione tifoso = segue("tifoso", "412|eccellenza|regionali|");
        pianificatore().giro();

        // Stessa partita (stesso id), rinviata di mezz'ora dal comunicato.
        calendario.remove(alle15);
        calendario.add(
                new Partita(
                        alle15.id(), alle15.dataOra().plusMinutes(30), alle15.stato(), alle15.impiantoId(),
                        alle15.casa(), alle15.ospite(), alle15.campionato(), alle15.ente(), alle15.girone(),
                        alle15.giornata(), alle15.casaSocietaId(), alle15.ospiteSocietaId(), alle15.casaSquadra(),
                        alle15.ospiteSquadra()));
        orologio.avanti(Duration.ofMinutes(30));
        pianificatore().giro();

        verify(invio, times(2)).invia(eq(tifoso), anyString(), any());
    }

    @Test
    void mezzOraPrimaLePartiteEntroIlRaggio() throws Exception {
        partitaAlle(14, 30);
        // 0,0207 gradi di latitudine sono 2,3 km.
        Iscrizione vicino = vicinoA("vicino", LAT_CAMPO + 0.0207, LNG_CAMPO, 5);
        Iscrizione lontano = vicinoA("lontano", LAT_CAMPO + 0.27, LNG_CAMPO, 20); // 30 km
        Iscrizione raggioStretto = vicinoA("stretto", LAT_CAMPO + 0.0207, LNG_CAMPO, 2);

        pianificatore().giro();

        List<JsonNode> mandate = mandateA(vicino);
        assertThat(mandate).hasSize(1);
        assertThat(mandate.getFirst().get("titolo").asText())
                .isEqualTo("Tra mezz'ora, a 2,3 km: LODIGIANI CALCIO 1972 – VIS SEZZE");
        assertThat(mandate.getFirst().get("url").asText()).isEqualTo("/societa/campo-lodigiani");
        assertThat(mandateA(lontano)).isEmpty();
        assertThat(mandateA(raggioStretto)).isEmpty();
        verify(invio).invia(eq(vicino), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void laFinestraDelleVicineVaDa25A35Minuti() throws Exception {
        partitaAlle(14, 24);
        partitaAlle(14, 25);
        partitaAlle(14, 34);
        partitaAlle(14, 35);
        partitaAlle(15, 0); // quella da un'ora non è per chi vuole le vicine
        Iscrizione vicino = vicinoA("vicino", LAT_CAMPO, LNG_CAMPO, 10);

        pianificatore().giro();

        assertThat(mandateA(vicino))
                .extracting(notifica -> notifica.get("testo").asText().substring(0, 10))
                .containsExactlyInAnyOrder("Alle 14:25", "Alle 14:34");
    }

    @Test
    void unCampoSenzaCoordinateNonEVicinoANessuno() throws Exception {
        when(campi.findByAnagraficaImpiantoIdIn(any()))
                .thenReturn(List.of(new Societa().setId("x").setAnagraficaImpiantoId(IMPIANTO)));
        partitaAlle(14, 30);
        Iscrizione vicino = vicinoA("vicino", LAT_CAMPO, LNG_CAMPO, 50);

        pianificatore().giro();

        assertThat(mandateA(vicino)).isEmpty();
    }

    @Test
    void unaIscrizioneScadutaSiCancellaENonRiceveAltro() throws Exception {
        partitaAlle(15, 0);
        partitaAlle(15, 2);
        Iscrizione vecchia = segue("vecchia", "412|eccellenza|regionali|");
        when(invio.invia(eq(vecchia), anyString(), any())).thenReturn(InvioWebPush.Esito.SCADUTA);

        PianificatoreNotifiche.Resoconto resoconto = pianificatore().giro();

        assertThat(resoconto.scadute()).isEqualTo(1);
        verify(iscrizioni).deleteById(vecchia.id());
        verify(invio, times(1)).invia(eq(vecchia), anyString(), any());
    }

    @Test
    void unInvioNonRiuscitoSiRiprovaAlGiroDopo() throws Exception {
        partitaAlle(15, 0);
        Iscrizione tifoso = segue("tifoso", "412|eccellenza|regionali|");
        when(invio.invia(eq(tifoso), anyString(), any()))
                .thenReturn(InvioWebPush.Esito.ERRORE, InvioWebPush.Esito.CONSEGNATA);

        PianificatoreNotifiche.Resoconto primo = pianificatore().giro();
        orologio.avanti(Duration.ofMinutes(5));
        PianificatoreNotifiche.Resoconto secondo = pianificatore().giro();

        assertThat(primo.errori()).isEqualTo(1);
        assertThat(secondo.mandate()).isEqualTo(1);
        verify(iscrizioni, never()).deleteById(any());
    }

    @Test
    void senzaIscrittiNonSiChiedeNienteAPresenze() {
        partitaAlle(15, 0);

        pianificatore().giro();

        verify(partite, never()).fra(any(), any());
    }

    @Test
    void distanzaConHaversine() {
        // Roma Termini – Colosseo, circa 1,6 km.
        assertThat(PianificatoreNotifiche.distanzaKm(41.9010, 12.5016, 41.8902, 12.4922))
                .isBetween(1.3, 1.6);
    }
}
