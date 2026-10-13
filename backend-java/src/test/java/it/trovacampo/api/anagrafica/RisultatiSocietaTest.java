package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * La scheda deve poter dire tre cose diverse: ecco i risultati, non ne ha
 * ancora giocate, presenze non risponde. Qui si controlla che il backend le
 * distingua, invece di mandare un elenco vuoto per tutte e tre.
 */
class RisultatiSocietaTest {

    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final SocietaRepository repository = mock(SocietaRepository.class);
    private final RisultatiSocieta risultati =
            new RisultatiSocieta(anagrafica, new SocietaInPresenze(anagrafica, repository));

    private static final Societa BOREALE = new Societa().setId("m1").setAnagraficaSocietaId(7L);

    private static AnagraficaPresenze.Risultato vittoria() {
        return new AnagraficaPresenze.Risultato(
                9912L,
                50L,
                OffsetDateTime.parse("2026-10-11T10:30:00+02:00"),
                44L,
                "UNDER 17 PROVINCIALE",
                "Roma",
                "2026-2027",
                77L,
                "A",
                3,
                false,
                "",
                "A.S.D. ESEMPIO",
                "ALTRA A.S.D.",
                2,
                1,
                true,
                "VITTORIA");
    }

    private static AnagraficaPresenze.Risultato sconfittaFuoriCasa() {
        return new AnagraficaPresenze.Risultato(
                9900L,
                51L,
                OffsetDateTime.parse("2026-10-04T11:00:00+02:00"),
                44L,
                "UNDER 17 PROVINCIALE",
                null,
                "2026-2027",
                null,
                null,
                2,
                false,
                "B",
                "TERZA A.S.D.",
                "A.S.D. ESEMPIO",
                3,
                0,
                false,
                "SCONFITTA");
    }

    @Test
    void iRisultatiArrivanoNellOrdineDiPresenzeDalPiuRecente() {
        when(anagrafica.risultati(7L))
                .thenReturn(
                        new AnagraficaPresenze.Risultati(
                                new AnagraficaPresenze.Riferimento(7L, "A.S.D. ESEMPIO"),
                                List.of(vittoria(), sconfittaFuoriCasa())));

        RisultatiSocieta.Risultati letti = risultati.di(BOREALE);

        assertThat(letti.stato()).isEqualTo(RisultatiSocieta.Stato.DISPONIBILI);
        assertThat(letti.risultati())
                .extracting(RisultatiSocieta.Risultato::giornata)
                .containsExactly(3, 2);
    }

    /** L'esito lo decide presenze: qui non si riconfrontano i gol. */
    @Test
    void esitoSquadraEDaCheParteGiocavaPassanoComeSono() {
        when(anagrafica.risultati(anyLong()))
                .thenReturn(
                        new AnagraficaPresenze.Risultati(
                                new AnagraficaPresenze.Riferimento(7L, "A.S.D. ESEMPIO"),
                                List.of(vittoria(), sconfittaFuoriCasa())));

        List<RisultatiSocieta.Risultato> letti = risultati.di(BOREALE).risultati();

        assertThat(letti.getFirst().esito()).isEqualTo("VITTORIA");
        assertThat(letti.getFirst().inCasa()).isTrue();
        assertThat(letti.getFirst().squadra()).isEmpty();
        assertThat(letti.getFirst().golCasa()).isEqualTo(2);
        assertThat(letti.getFirst().golOspite()).isEqualTo(1);
        assertThat(letti.getLast().esito()).isEqualTo("SCONFITTA");
        assertThat(letti.getLast().inCasa()).isFalse();
        assertThat(letti.getLast().squadra()).isEqualTo("B");
        // Ente e girone possono mancare: passano nulli, non vuoti.
        assertThat(letti.getLast().ente()).isNull();
        assertThat(letti.getLast().girone()).isNull();
    }

    /** A inizio stagione non c'è niente da mostrare, ma non è un guasto. */
    @Test
    void unElencoVuotoRestaDisponibile() {
        when(anagrafica.risultati(anyLong()))
                .thenReturn(
                        new AnagraficaPresenze.Risultati(
                                new AnagraficaPresenze.Riferimento(7L, "A.S.D. ESEMPIO"), List.of()));

        RisultatiSocieta.Risultati letti = risultati.di(BOREALE);

        assertThat(letti.stato()).isEqualTo(RisultatiSocieta.Stato.DISPONIBILI);
        assertThat(letti.risultati()).isEmpty();
    }

    /**
     * TrovaCampo si rilascia per conto suo: la scheda può arrivare sul server
     * prima dell'endpoint di presenze, e allora non deve promettere di
     * riprovare.
     */
    @Test
    void unPresenzeSenzaLEndpointNonEUnGuasto() {
        when(anagrafica.risultati(anyLong()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        RisultatiSocieta.Risultati letti = risultati.di(BOREALE);

        assertThat(letti.stato()).isEqualTo(RisultatiSocieta.Stato.NON_DISPONIBILI);
        assertThat(letti.risultati()).isEmpty();
    }

    @Test
    void unPresenzeGiuSiDistingueDaUnaSocietaSenzaRisultati() {
        when(anagrafica.risultati(anyLong()))
                .thenThrow(
                        HttpServerErrorException.create(
                                HttpStatus.INTERNAL_SERVER_ERROR, "Boom", null, null, null));

        assertThat(risultati.di(BOREALE).stato()).isEqualTo(RisultatiSocieta.Stato.ERRORE);
    }

    @Test
    void ancheUnTimeoutEUnErrore() {
        when(anagrafica.risultati(anyLong()))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThat(risultati.di(BOREALE).stato()).isEqualTo(RisultatiSocieta.Stato.ERRORE);
    }

    /** Un campo inserito a mano, con un nome che presenze non riconosce. */
    @Test
    void unaSocietaCheNonCeInPresenzeNonSiChiede() {
        when(anagrafica.cerca("Campetto del quartiere")).thenReturn(List.of());

        RisultatiSocieta.Risultati letti =
                risultati.di(new Societa().setNomeSocieta("Campetto del quartiere"));

        assertThat(letti.stato()).isEqualTo(RisultatiSocieta.Stato.NON_DISPONIBILI);
        verify(anagrafica, never()).risultati(anyLong());
    }

    /** Una società non ancora legata si cerca per nome, come per le squadre. */
    @Test
    void unaSocietaNonLegataSiTrovaPerNomeESiLega() {
        Societa daFile = new Societa().setId("m2").setNomeSocieta("A.S.D. Esempio");
        when(anagrafica.cerca("A.S.D. Esempio"))
                .thenReturn(List.of(new AnagraficaPresenze.Riferimento(7L, "A.S.D. ESEMPIO")));
        when(anagrafica.risultati(7L))
                .thenReturn(
                        new AnagraficaPresenze.Risultati(
                                new AnagraficaPresenze.Riferimento(7L, "A.S.D. ESEMPIO"),
                                List.of(vittoria())));

        assertThat(risultati.di(daFile).risultati()).hasSize(1);
        assertThat(daFile.getAnagraficaSocietaId()).isEqualTo(7L);
        verify(repository).save(daFile);
    }

    /**
     * L'ora che si legge nella scheda e' quella del comunicato, cioe' quella
     * italiana: Jackson porta la data a UTC leggendola, e qui torna su Roma.
     */
    @Test
    void laDataTornaSulFusoDiRoma() {
        when(anagrafica.risultati(anyLong()))
                .thenReturn(
                        new AnagraficaPresenze.Risultati(
                                new AnagraficaPresenze.Riferimento(7L, "A.S.D. ESEMPIO"),
                                List.of(
                                        new AnagraficaPresenze.Risultato(
                                                9912L,
                                                50L,
                                                OffsetDateTime.parse("2026-10-11T08:30:00Z"),
                                                44L, "UNDER 17 PROVINCIALE", "Roma", "2026-2027", 77L,
                                                "A", 3, false, "", "A.S.D. ESEMPIO", "ALTRA A.S.D.",
                                                2, 1, true, "VITTORIA"))));

        assertThat(risultati.di(BOREALE).risultati().getFirst().data())
                .isEqualTo(OffsetDateTime.parse("2026-10-11T10:30:00+02:00"))
                .hasToString("2026-10-11T10:30+02:00");
    }

    /** Una partita di cui presenze non sa l'ora: la data manca, e non si inventa. */
    @Test
    void unaDataMancanteRestaMancante() {
        AnagraficaPresenze.Risultato senzaData =
                new AnagraficaPresenze.Risultato(
                        9800L, 52L, null, 44L, "UNDER 17 PROVINCIALE", "Roma", "2026-2027", 77L, "A", 1,
                        false, "", "A.S.D. ESEMPIO", "QUARTA A.S.D.", 1, 1, true, "PAREGGIO");
        when(anagrafica.risultati(anyLong()))
                .thenReturn(
                        new AnagraficaPresenze.Risultati(
                                new AnagraficaPresenze.Riferimento(7L, "A.S.D. ESEMPIO"),
                                List.of(senzaData)));

        assertThat(risultati.di(BOREALE).risultati().getFirst().data()).isNull();
    }
}
