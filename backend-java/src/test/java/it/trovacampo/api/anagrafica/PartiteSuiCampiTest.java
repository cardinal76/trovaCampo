package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PartiteSuiCampiTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    /** Venerdì 4 settembre 2026, alle dieci. */
    private static final Instant VENERDI = LocalDateTime.of(2026, 9, 4, 10, 0).atZone(ROMA).toInstant();

    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final OrologioAMano orologio = new OrologioAMano(VENERDI);
    private final SocietaRepository societa = mock(SocietaRepository.class);
    private final PartiteSuiCampi partite = new PartiteSuiCampi(anagrafica, societa, orologio);

    /** Un orologio che si manda avanti, per la cache. */
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

    private static AnagraficaPresenze.Partita partita(
            long impianto, LocalDateTime quando, String casa, String stato) {
        return new AnagraficaPresenze.Partita(
                1L,
                quando.atZone(ROMA).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC),
                stato,
                impianto,
                casa,
                "OSPITE",
                "ECCELLENZA",
                "Regionali",
                "A",
                1);
    }

    private static AnagraficaPresenze.Partita partita(long impianto, LocalDateTime quando, String casa) {
        return partita(impianto, quando, casa, "DA_GIOCARE");
    }

    private static Societa campo(Long impianto) {
        return new Societa().setId("m1").setNomeSocieta("BOREALE").setAnagraficaImpiantoId(impianto);
    }

    @Test
    void sulCampoSoloLePartiteDaGiocareDiQuelCampoConIlFusoDiRoma() {
        when(anagrafica.partite(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 17)))
                .thenReturn(
                        List.of(
                                partita(190, LocalDateTime.of(2026, 9, 6, 15, 30), "JUNIORES"),
                                partita(190, LocalDateTime.of(2026, 9, 6, 11, 0), "BOREALE"),
                                partita(191, LocalDateTime.of(2026, 9, 6, 11, 0), "ALTRO CAMPO"),
                                partita(190, LocalDateTime.of(2026, 9, 5, 18, 0), "RINVIATA", "RINVIATA"),
                                // Giocata stamattina alle sette: è passata.
                                partita(190, LocalDateTime.of(2026, 9, 4, 7, 0), "PASSATA"),
                                // Cominciata alle nove: si sta giocando, si vede.
                                partita(190, LocalDateTime.of(2026, 9, 4, 9, 0), "IN CORSO")));

        assertThat(partite.sulCampo(campo(190L)))
                .extracting(PartiteSuiCampi.Partita::casa)
                .containsExactly("IN CORSO", "BOREALE", "JUNIORES");
        assertThat(partite.sulCampo(campo(190L)).get(1).dataOra())
                .isEqualTo(OffsetDateTime.of(2026, 9, 6, 11, 0, 0, 0, ZoneOffset.ofHours(2)));
        // Partite senza gli id delle società: nessuno stemma da cercare.
        verify(societa, never()).stemmiDi(any());
    }

    @Test
    void nellaSchedaOgniSquadraHaLoStemmaCheTrovaCampoConosce() {
        AnagraficaPresenze.Partita conSocieta = new AnagraficaPresenze.Partita(
                1L,
                LocalDateTime.of(2026, 9, 6, 11, 0).atZone(ROMA).toOffsetDateTime(),
                "DA_GIOCARE",
                190L,
                "BOREALE",
                "VIGOR PERCONTI",
                "ECCELLENZA",
                "Regionali",
                "A",
                1,
                10L,
                20L,
                "",
                "");
        when(anagrafica.partite(any(), any())).thenReturn(List.of(conSocieta));
        // La Boreale ha due campi, quindi due righe; la Vigor uno stemma non ce l'ha.
        when(societa.stemmiDi(any()))
                .thenReturn(
                        List.of(
                                new Societa().setAnagraficaSocietaId(10L).setLogoUrl("https://lnd.it/boreale.png"),
                                new Societa().setAnagraficaSocietaId(10L).setLogoUrl("https://lnd.it/altro.png")));

        PartiteSuiCampi.Partita partita = partite.sulCampo(campo(190L)).get(0);

        assertThat(partita.casaLogoUrl()).isEqualTo("https://lnd.it/boreale.png");
        assertThat(partita.ospiteLogoUrl()).isNull();
        // La mappa gli stemmi non li mostra e non li cerca.
        assertThat(partite.perLaMappa().get(190L).get(0).casaLogoUrl()).isNull();
        verify(societa, times(1)).stemmiDi(any());
    }

    @Test
    void unCampoCheNonVieneDaPresenzeNonHaPartiteENonChiedeNiente() {
        assertThat(partite.sulCampo(campo(null))).isEmpty();
        verify(anagrafica, never()).partite(any(), any());
    }

    @Test
    void laSchedaGuardaDueSettimaneLaMappaUna() {
        when(anagrafica.partite(any(), any()))
                .thenReturn(
                        List.of(
                                partita(190, LocalDateTime.of(2026, 9, 10, 20, 0), "GIOVEDI"),
                                partita(190, LocalDateTime.of(2026, 9, 12, 15, 0), "SABATO DOPO")));

        assertThat(partite.sulCampo(campo(190L)))
                .extracting(PartiteSuiCampi.Partita::casa)
                .containsExactly("GIOVEDI", "SABATO DOPO");
        // Da venerdì 4 a giovedì 10 compreso.
        assertThat(partite.perLaMappa().get(190L))
                .extracting(PartiteSuiCampi.Partita::casa)
                .containsExactly("GIOVEDI");
    }

    @Test
    void laMappaHaLePrimeTrePartiteDiOgniCampoESoloICampiConPartite() {
        List<AnagraficaPresenze.Partita> tante = new ArrayList<>();
        for (int ora = 9; ora < 17; ora++) {
            tante.add(partita(190, LocalDateTime.of(2026, 9, 6, ora, 0), "ORE " + ora));
        }
        tante.add(partita(191, LocalDateTime.of(2026, 9, 5, 15, 0), "SABATO"));
        when(anagrafica.partite(any(), any())).thenReturn(tante);

        var mappa = partite.perLaMappa();

        assertThat(mappa).containsOnlyKeys(190L, 191L);
        assertThat(mappa.get(190L))
                .extracting(PartiteSuiCampi.Partita::casa)
                .containsExactly("ORE 9", "ORE 10", "ORE 11");
    }

    @Test
    void presenzeSiChiamaUnaVoltaOgniCinqueMinuti() {
        when(anagrafica.partite(any(), any()))
                .thenReturn(List.of(partita(190, LocalDateTime.of(2026, 9, 6, 11, 0), "BOREALE")));

        partite.perLaMappa();
        partite.sulCampo(campo(190L));
        orologio.avanti(Duration.ofMinutes(4));
        partite.perLaMappa();
        verify(anagrafica, times(1)).partite(any(), any());

        orologio.avanti(Duration.ofMinutes(2));
        partite.perLaMappa();
        verify(anagrafica, times(2)).partite(any(), any());
    }

    @Test
    void sePresenzeNonRispondeSiTengonoQuelleDiPrimaESiAspettaUnMinuto() {
        when(anagrafica.partite(any(), any()))
                .thenReturn(List.of(partita(190, LocalDateTime.of(2026, 9, 6, 11, 0), "BOREALE")))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(partite.perLaMappa()).containsKey(190L);
        orologio.avanti(Duration.ofMinutes(6));
        // Non risponde: le partite dell'ultima lettura restano.
        assertThat(partite.perLaMappa()).containsKey(190L);
        orologio.avanti(Duration.ofSeconds(30));
        partite.perLaMappa();
        verify(anagrafica, times(2)).partite(any(), any());
    }

    @Test
    void sePresenzeNonHaMaiRispostoNientePartiteMaNessunErrore() {
        when(anagrafica.partite(any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(partite.perLaMappa()).isEmpty();
        assertThat(partite.sulCampo(campo(190L))).isEmpty();
    }

    @Test
    void perLeNotificheSoloQuelleDaGiocareNellIntervallo() {
        // [10:30, 11:30): dentro quella delle 10:30 e quella delle 11:00, fuori
        // quella delle 11:30 (estremo escluso), quella già cominciata e quella rinviata.
        when(anagrafica.partite(any(), any()))
                .thenReturn(
                        List.of(
                                partita(190L, LocalDateTime.of(2026, 9, 4, 11, 30), "FUORI DOPO"),
                                partita(190L, LocalDateTime.of(2026, 9, 4, 11, 0), "ALLE UNDICI"),
                                partita(191L, LocalDateTime.of(2026, 9, 4, 10, 30), "ALLE DIECI E MEZZA"),
                                partita(190L, LocalDateTime.of(2026, 9, 4, 10, 0), "GIA INIZIATA"),
                                partita(190L, LocalDateTime.of(2026, 9, 4, 11, 0), "RINVIATA", "RINVIATA")));

        assertThat(partite.fra(VENERDI.plus(Duration.ofMinutes(30)), VENERDI.plus(Duration.ofMinutes(90))))
                .extracting(AnagraficaPresenze.Partita::casa)
                .containsExactly("ALLE DIECI E MEZZA", "ALLE UNDICI");
    }
}
