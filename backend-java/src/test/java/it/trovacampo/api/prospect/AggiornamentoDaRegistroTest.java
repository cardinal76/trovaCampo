package it.trovacampo.api.prospect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Il passaggio dalle voci del registro ai prospect, senza giro intorno: se si
 * puo' fare un'altra richiesta lo decide la {@code Richiesta} che il test passa.
 */
class AggiornamentoDaRegistroTest {

    private static final Instant ADESSO = Instant.parse("2026-11-10T10:00:00Z");
    private static final Duration VALIDITA = Duration.ofDays(30);

    private final RegistroRasd registro = mock(RegistroRasd.class);
    private final ProspectRepository repository = mock(ProspectRepository.class);
    private final AggiornamentoDaRegistro aggiornamento =
            new AggiornamentoDaRegistro(registro, repository, VALIDITA, () -> ADESSO);
    private final ContatoriGiro contatori = new ContatoriGiro();

    private static RegistroRasd.Voce voce(long id, String codiceFiscale, String denominazione, String... organismi) {
        return new RegistroRasd.Voce(
                id, codiceFiscale, denominazione, "Lazio", "Roma", List.of(organismi), "ASD", LocalDate.of(2020, 1, 1));
    }

    private static final RegistroRasd.Dettaglio CALCIO_A_5 =
            new RegistroRasd.Dettaglio(List.of("Calcio a 5"), 40, 3, 12);

    @SuppressWarnings("unchecked")
    private List<Prospect> salvati() {
        ArgumentCaptor<List<Prospect>> catturati = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(catturati.capture());
        return catturati.getValue();
    }

    @Test
    void creaIProspectNuoviEContaLeVoci() throws Exception {
        List<RegistroRasd.Voce> voci = List.of(voce(1, "CF1", "A.S.D. UNO", "FIGC"), voce(2, "CF2", "A.S.D. DUE", "CSI"));
        when(repository.findAllById(List.of("CF1", "CF2"))).thenReturn(List.of());

        boolean completa = aggiornamento.aggiorna(contatori, voci, AmbitoDettagli.NESSUNO, () -> true);

        assertThat(completa).isTrue();
        assertThat(contatori.nuove).isEqualTo(2);
        assertThat(contatori.lette).isEqualTo(2);
        List<Prospect> salvati = salvati();
        assertThat(salvati).extracting(Prospect::getCodiceFiscale).containsExactly("CF1", "CF2");
        assertThat(salvati.get(0).getDenominazione()).isEqualTo("A.S.D. UNO");
        // FIGC: calcio anche senza il dettaglio. CSI senza discipline: no.
        assertThat(salvati.get(0).isCalcio()).isTrue();
        assertThat(salvati.get(1).isCalcio()).isFalse();
    }

    @Test
    void aggiornaQuelloCheCeSenzaPerdereIlCampoGiaAssegnato() throws Exception {
        Prospect esistente =
                new Prospect().setCodiceFiscale("CF1").setDenominazione("VECCHIO NOME").setLat(41.9).setLng(12.5);
        when(repository.findAllById(List.of("CF1"))).thenReturn(List.of(esistente));

        aggiornamento.aggiorna(contatori, List.of(voce(1, "CF1", "NUOVO NOME", "FIGC")), AmbitoDettagli.NESSUNO, () -> true);

        assertThat(contatori.nuove).isZero();
        assertThat(esistente.getDenominazione()).isEqualTo("NUOVO NOME");
        assertThat(esistente.getLat()).isEqualTo(41.9);
        assertThat(esistente.getAggiornato()).isEqualTo(ADESSO);
    }

    @Test
    void chiedeIlDettaglioSoloAChiLoAmbitoVuoleELoSalvaNelProspect() throws Exception {
        when(repository.findAllById(List.of("CF1", "CF2"))).thenReturn(List.of());
        when(registro.dettaglio(1)).thenReturn(CALCIO_A_5);
        List<RegistroRasd.Voce> voci = List.of(voce(1, "CF1", "A.S.D. UNO", "FIGC"), voce(2, "CF2", "A.S.D. DUE", "CSI"));

        aggiornamento.aggiorna(contatori, voci, AmbitoDettagli.FIGC, () -> true);

        assertThat(contatori.dettagli).isEqualTo(1);
        verify(registro, never()).dettaglio(2);
        Prospect uno = salvati().get(0);
        assertThat(uno.getDiscipline()).containsExactly("Calcio a 5");
        assertThat(uno.getTesseratiAttivi()).isEqualTo(40);
        assertThat(uno.getDettaglioLetto()).isEqualTo(ADESSO);
    }

    @Test
    void unDettaglioLettoDaPocoNonSiRichiedeEViene_contatoComeSaltato() throws Exception {
        Prospect recente =
                new Prospect().setCodiceFiscale("CF1").setDettaglioLetto(ADESSO.minus(VALIDITA).plusSeconds(60));
        when(repository.findAllById(List.of("CF1"))).thenReturn(List.of(recente));

        aggiornamento.aggiorna(contatori, List.of(voce(1, "CF1", "A.S.D. UNO", "FIGC")), AmbitoDettagli.TUTTE, () -> true);

        assertThat(contatori.saltati).isEqualTo(1);
        assertThat(contatori.dettagli).isZero();
        verify(registro, never()).dettaglio(anyLong());
    }

    @Test
    void unDettaglioScadutoSiRichiedeDiNuovo() throws Exception {
        Prospect vecchio =
                new Prospect().setCodiceFiscale("CF1").setDettaglioLetto(ADESSO.minus(VALIDITA).minusSeconds(60));
        when(repository.findAllById(List.of("CF1"))).thenReturn(List.of(vecchio));
        when(registro.dettaglio(1)).thenReturn(CALCIO_A_5);

        aggiornamento.aggiorna(contatori, List.of(voce(1, "CF1", "A.S.D. UNO", "FIGC")), AmbitoDettagli.TUTTE, () -> true);

        assertThat(contatori.dettagli).isEqualTo(1);
        assertThat(vecchio.getDettaglioLetto()).isEqualTo(ADESSO);
    }

    @Test
    void seNonSiPuoFareUnaRichiestaSiSalvaQuelloCheSiHaESiDiceChePaginaNonEFinita() throws Exception {
        when(repository.findAllById(List.of("CF1", "CF2", "CF3"))).thenReturn(List.of());
        when(registro.dettaglio(1)).thenReturn(CALCIO_A_5);
        // la prima richiesta passa, la seconda no (arrivato un «Ferma»)
        List<Boolean> risposte = new ArrayList<>(List.of(true, false));
        List<RegistroRasd.Voce> voci =
                List.of(voce(1, "CF1", "UNO", "FIGC"), voce(2, "CF2", "DUE", "FIGC"), voce(3, "CF3", "TRE", "FIGC"));

        boolean completa =
                aggiornamento.aggiorna(contatori, voci, AmbitoDettagli.FIGC, () -> risposte.isEmpty() ? false : risposte.remove(0));

        assertThat(completa).isFalse();
        assertThat(contatori.dettagli).isEqualTo(1);
        // tutte e tre le voci sono salvate, anche quelle senza dettaglio: la pagina si rilegge al prossimo giro
        assertThat(salvati()).hasSize(3);
        assertThat(contatori.lette).isEqualTo(3);
        verify(registro, never()).dettaglio(2);
    }

    @Test
    void ilRegistroCheBloccaAMetaPaginaNonFaPerdereIDettagliGiaLetti() throws Exception {
        when(repository.findAllById(List.of("CF1", "CF2"))).thenReturn(List.of());
        when(registro.dettaglio(1)).thenReturn(CALCIO_A_5);
        when(registro.dettaglio(2)).thenThrow(new RegistroRasd.RegistroBloccatoException("403"));
        List<RegistroRasd.Voce> voci = List.of(voce(1, "CF1", "UNO", "FIGC"), voce(2, "CF2", "DUE", "FIGC"));

        try {
            aggiornamento.aggiorna(contatori, voci, AmbitoDettagli.FIGC, () -> true);
        } catch (RegistroRasd.RegistroBloccatoException atteso) {
            // il blocco risale a chi chiama, che lo scrive nel giro
        }

        List<Prospect> salvati = salvati();
        assertThat(salvati).extracting(Prospect::getCodiceFiscale).containsExactly("CF1");
        assertThat(salvati.get(0).getDettaglioLetto()).isEqualTo(ADESSO);
    }

    @Test
    void unaPaginaVuotaENonUnProblema() throws Exception {
        assertThat(aggiornamento.aggiorna(contatori, List.of(), AmbitoDettagli.TUTTE, () -> false)).isTrue();
        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void calcioSeAffiliataFigcOCoDisciplinaChePartePerCalcio() {
        assertThat(AggiornamentoDaRegistro.calcio(List.of("CSI", "FIGC"), null)).isTrue();
        assertThat(AggiornamentoDaRegistro.calcio(List.of("CSI"), List.of("Calcio a 5"))).isTrue();
        assertThat(AggiornamentoDaRegistro.calcio(List.of("FIS"), List.of("Sciabola"))).isFalse();
        assertThat(AggiornamentoDaRegistro.calcio(null, null)).isFalse();
    }
}
