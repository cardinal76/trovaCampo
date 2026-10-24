package it.trovacampo.api.notifiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.anagrafica.AnagraficaPresenze.Partita;
import it.trovacampo.api.anagrafica.PartiteSuiCampi;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.notifiche.ProssimePartite.AvvisoPrevisto;
import it.trovacampo.api.notifiche.ProssimePartite.Elenco;
import it.trovacampo.api.notifiche.ProssimePartite.ProssimaPartita;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * La pagina Prossime partite con le partite finte: quali partite, con quale
 * avviso, a che ora, e se è già arrivato. Le partite le sceglie la stessa
 * regola del pianificatore ({@link PartiteDaAvvisare}).
 */
class ProssimePartiteTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    /** Sabato 5 settembre 2026, alle 10:00 di Roma. */
    private static final LocalDateTime SABATO_10 = LocalDateTime.of(2026, 9, 5, 10, 0);

    private static final long LODIGIANI = 412L;
    private static final long VIS_SEZZE = 77L;
    private static final long IMPIANTO = 190L;
    private static final double LAT_CAMPO = 41.9000;
    private static final double LNG_CAMPO = 12.5000;
    private static final String SEGUE_LODIGIANI = "412|eccellenza|regionali|";
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/questo-browser";

    private final PartiteSuiCampi partite = mock(PartiteSuiCampi.class);
    private final SocietaRepository campi = mock(SocietaRepository.class);
    private final Set<String> prenotati = new HashSet<>();
    private final List<Partita> calendario = new ArrayList<>();
    private Instant adesso = SABATO_10.atZone(ROMA).toInstant();

    private final RegistroInvii registro =
            new RegistroInvii() {
                @Override
                public boolean prenota(String chiave) {
                    return prenotati.add(chiave);
                }

                @Override
                public void annulla(String chiave) {
                    prenotati.remove(chiave);
                }

                @Override
                public Set<String> giaPrenotati(Collection<String> chiavi) {
                    return chiavi.stream().filter(prenotati::contains).collect(Collectors.toSet());
                }
            };

    @BeforeEach
    void prepara() {
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
                                        .setNomeSocieta("LODIGIANI CALCIO 1972")
                                        .setNomeImpianto("La Borghesiana \"C\"")
                                        .setIndirizzoImpianto("Via di Vermicino 105")
                                        .setLocalitaImpianto("Roma")
                                        .setAnagraficaSocietaId(LODIGIANI)
                                        .setAnagraficaImpiantoId(IMPIANTO)
                                        .setLat(LAT_CAMPO)
                                        .setLng(LNG_CAMPO)));
    }

    private ProssimePartite prossime() {
        Clock orologio = Clock.fixed(adesso, ROMA);
        return new ProssimePartite(new PartiteDaAvvisare(partite, campi), registro, orologio);
    }

    /** LODIGIANI (prima squadra) – VIS SEZZE ("B"), in Eccellenza, fra {@code giorni} giorni. */
    private Partita partita(long id, int giorni, int ora, int minuti) {
        Partita partita =
                new Partita(
                        id,
                        SABATO_10.plusDays(giorni).withHour(ora).withMinute(minuti).atZone(ROMA)
                                .toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC),
                        "DA_GIOCARE",
                        IMPIANTO,
                        "LODIGIANI CALCIO 1972",
                        "VIS SEZZE",
                        7L,
                        "ECCELLENZA",
                        "Regionali",
                        32L,
                        "B",
                        3,
                        LODIGIANI,
                        VIS_SEZZE,
                        "",
                        "B",
                        null);
        calendario.add(partita);
        return partita;
    }

    private static ProssimeRequest squadre(String... chiavi) {
        return new ProssimeRequest(List.of(chiavi), null, null, null);
    }

    private static ProssimeRequest vicino(double lat, double lng, int raggioKm) {
        return new ProssimeRequest(List.of(), new IscrizioneRequest.Posizione(lat, lng), raggioKm, null);
    }

    private static String statoDelPrimo(Elenco elenco) {
        return elenco.partite().getFirst().avvisi().getFirst().stato();
    }

    @Test
    void lePartiteDiUnaSquadraSeguitaConLOraDellAvviso() {
        partita(1, 1, 15, 0);

        Elenco elenco = prossime().elenco(squadre(SEGUE_LODIGIANI));

        assertThat(elenco.partite()).hasSize(1);
        ProssimaPartita riga = elenco.partite().getFirst();
        assertThat(riga.casa()).isEqualTo("LODIGIANI CALCIO 1972");
        assertThat(riga.dataOra().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 6, 15, 0));
        assertThat(riga.campo().id()).isEqualTo("campo-lodigiani");
        assertThat(riga.campo().indirizzo()).isEqualTo("Via di Vermicino 105");
        assertThat(riga.avvisi()).hasSize(1);
        AvvisoPrevisto avviso = riga.avvisi().getFirst();
        assertThat(avviso.tipo()).isEqualTo("squadra");
        assertThat(avviso.seguite()).containsExactly("LODIGIANI CALCIO 1972");
        assertThat(avviso.distanzaKm()).isNull();
        assertThat(avviso.alle().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 6, 14, 0));
        assertThat(avviso.stato()).isEqualTo(ProssimePartite.PREVISTO);
        assertThat(elenco.fino()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(elenco.altre()).isFalse();
    }

    @Test
    void conLaStessaRegolaDelPianificatore() {
        // La VIS SEZZE che gioca è la "B": chi segue la prima non la vede, come non riceve l'avviso.
        partita(1, 1, 15, 0);

        assertThat(prossime().elenco(squadre("77|eccellenza|regionali|")).partite()).isEmpty();
        assertThat(prossime().elenco(squadre("77|eccellenza|regionali|b")).partite()).hasSize(1);
        assertThat(prossime().elenco(squadre("412|juniores under 19 regionale|regionali|")).partite())
                .isEmpty();
    }

    @Test
    void lePartiteVicineConLaDistanzaEMezzOraPrima() {
        partita(1, 1, 15, 0);
        // 0,0207 gradi di latitudine sono 2,3 km.

        Elenco vicine = prossime().elenco(vicino(LAT_CAMPO + 0.0207, LNG_CAMPO, 5));
        Elenco lontane = prossime().elenco(vicino(LAT_CAMPO + 0.0207, LNG_CAMPO, 2));

        assertThat(vicine.partite()).hasSize(1);
        AvvisoPrevisto avviso = vicine.partite().getFirst().avvisi().getFirst();
        assertThat(avviso.tipo()).isEqualTo("vicino");
        assertThat(avviso.distanzaKm()).isEqualTo(2.3);
        assertThat(avviso.seguite()).isEmpty();
        assertThat(avviso.alle().toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 9, 6, 14, 30));
        assertThat(lontane.partite()).isEmpty();
    }

    @Test
    void unaPartitaSeguitaEVicinaHaTuttiEDueGliAvvisi() {
        partita(1, 1, 15, 0);

        Elenco elenco =
                prossime()
                        .elenco(
                                new ProssimeRequest(
                                        List.of(SEGUE_LODIGIANI),
                                        new IscrizioneRequest.Posizione(LAT_CAMPO, LNG_CAMPO),
                                        10,
                                        null));

        assertThat(elenco.partite()).hasSize(1);
        assertThat(elenco.partite().getFirst().avvisi())
                .extracting(AvvisoPrevisto::tipo)
                .containsExactly("squadra", "vicino");
    }

    @Test
    void perGiornoEOrarioDaAdessoAOggiPiuSette() {
        partita(1, 0, 9, 0); // stamattina: è già cominciata
        partita(2, 0, 16, 30);
        partita(3, 1, 11, 0);
        partita(4, 7, 18, 0); // sabato dopo: l'ultimo giorno
        partita(5, 8, 15, 0); // domenica dopo: fuori

        Elenco elenco = prossime().elenco(squadre(SEGUE_LODIGIANI));

        assertThat(elenco.partite())
                .extracting(riga -> riga.dataOra().toLocalDateTime())
                .containsExactly(
                        LocalDateTime.of(2026, 9, 5, 16, 30),
                        LocalDateTime.of(2026, 9, 6, 11, 0),
                        LocalDateTime.of(2026, 9, 12, 18, 0));
    }

    @Test
    void conLEndpointDiceQualiAvvisiSonoArrivati() {
        Partita alle11 = partita(1, 0, 11, 0);
        adesso = SABATO_10.withMinute(20).atZone(ROMA).toInstant();
        prenotati.add(PartiteDaAvvisare.chiaveInvio(Iscrizione.idDi(ENDPOINT), TipoAvviso.SQUADRA, alle11));

        Elenco conEndpoint =
                prossime().elenco(new ProssimeRequest(List.of(SEGUE_LODIGIANI), null, null, ENDPOINT));
        Elenco diUnAltro =
                prossime()
                        .elenco(
                                new ProssimeRequest(
                                        List.of(SEGUE_LODIGIANI), null, null,
                                        "https://fcm.googleapis.com/fcm/send/un-altro"));

        assertThat(conEndpoint.partite().getFirst().avvisi().getFirst().stato())
                .isEqualTo(ProssimePartite.INVIATO);
        assertThat(diUnAltro.partite().getFirst().avvisi().getFirst().stato())
                .isEqualTo(ProssimePartite.PASSATO);
    }

    @Test
    void lAvvisoNonArrivatoEPassatoSoloDopoIlGiroDiRiserva() {
        partita(1, 0, 11, 0); // avviso alle 10:00, giro di riserva alle 10:05

        adesso = SABATO_10.withMinute(3).atZone(ROMA).toInstant();
        String alle1003 = statoDelPrimo(prossime().elenco(squadre(SEGUE_LODIGIANI)));
        adesso = SABATO_10.withMinute(5).atZone(ROMA).toInstant();
        String alle1005 = statoDelPrimo(prossime().elenco(squadre(SEGUE_LODIGIANI)));

        assertThat(alle1003).isEqualTo(ProssimePartite.PREVISTO);
        assertThat(alle1005).isEqualTo(ProssimePartite.PASSATO);
    }

    @Test
    void senzaSquadreNePosizioneNonSiGuardaNiente() {
        partita(1, 1, 15, 0);

        Elenco elenco = prossime().elenco(squadre());

        assertThat(elenco.partite()).isEmpty();
        verify(partite, never()).fra(any(), any());
    }

    @Test
    void oltreIlMassimoSiFermaEloDice() {
        for (int i = 0; i < ProssimePartite.MASSIMO_PARTITE + 5; i++) {
            partita(i, 1 + i / 50, 8 + (i % 50) / 4, (i % 4) * 15);
        }

        Elenco elenco = prossime().elenco(squadre(SEGUE_LODIGIANI));

        assertThat(elenco.partite()).hasSize(ProssimePartite.MASSIMO_PARTITE);
        assertThat(elenco.altre()).isTrue();
    }

    @Test
    void laPosizioneSiArrotondaComeQuellaDellIscrizione() {
        partita(1, 1, 15, 0);
        // 41.90049 diventa 41.900: il campo è lì, a zero metri, come per il pianificatore.
        Elenco elenco = prossime().elenco(vicino(LAT_CAMPO + 0.00049, LNG_CAMPO, 5));

        assertThat(elenco.partite().getFirst().avvisi().getFirst().distanzaKm()).isZero();
    }
}
