package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class SquadreSocietaTest {

    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final SocietaRepository repository = mock(SocietaRepository.class);
    private final SquadreSocieta squadre = new SquadreSocieta(anagrafica, repository);

    private static AnagraficaPresenze.Scheda boreale() {
        return new AnagraficaPresenze.Scheda(
                7L,
                "BOREALE",
                List.of(),
                List.of(
                        new AnagraficaPresenze.Squadra(
                                "ECCELLENZA", "Regionali", "2026/2027", "A", "", false, null)));
    }

    @Test
    void unaSocietaCheNonCeInPresenzeNonHaSquadre() {
        when(anagrafica.cerca("Certosa Calcio")).thenReturn(List.of());

        assertThat(squadre.di(new Societa().setNomeSocieta("Certosa Calcio"))).isEmpty();
        verify(anagrafica, never()).scheda(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void unaSocietaNonLegataSiTrovaPerNomeESiLega() {
        Societa daFile = new Societa().setId("m1").setNomeSocieta("A.S.D. Boreale");
        when(anagrafica.cerca("A.S.D. Boreale"))
                .thenReturn(
                        List.of(
                                new AnagraficaPresenze.Riferimento(3L, "BOREALE DONORIONE"),
                                new AnagraficaPresenze.Riferimento(7L, "ASD BOREALE")));
        when(anagrafica.scheda(7)).thenReturn(boreale());

        assertThat(squadre.di(daFile))
                .extracting(SquadreSocieta.Squadra::campionato)
                .containsExactly("ECCELLENZA");
        assertThat(daFile.getAnagraficaSocietaId()).isEqualTo(7L);
        verify(repository).save(daFile);
    }

    @Test
    void unaSolaRispostaELeiAncheSeScrittaDiversamente() {
        when(anagrafica.cerca("Boreale Calcio"))
                .thenReturn(List.of(new AnagraficaPresenze.Riferimento(7L, "BOREALE")));
        when(anagrafica.scheda(7)).thenReturn(boreale());

        assertThat(squadre.di(new Societa().setNomeSocieta("Boreale Calcio"))).hasSize(1);
    }

    @Test
    void unNomeAmbiguoNonSiIndovina() {
        when(anagrafica.cerca("Roma"))
                .thenReturn(
                        List.of(
                                new AnagraficaPresenze.Riferimento(1L, "ROMA CALCIO"),
                                new AnagraficaPresenze.Riferimento(2L, "ROMA NORD")));

        assertThat(squadre.di(new Societa().setNomeSocieta("Roma"))).isEmpty();
        verify(anagrafica, never()).scheda(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void ogniSquadraPortaIlNomeDelSuoCampo() {
        when(anagrafica.scheda(7))
                .thenReturn(
                        new AnagraficaPresenze.Scheda(
                                7L,
                                "BOREALE",
                                List.of(
                                        new AnagraficaPresenze.Campo(
                                                190L, "DON ORIONE", "VIA DELLA CAMILLUCCIA 120", "ROMA")),
                                List.of(
                                        new AnagraficaPresenze.Squadra(
                                                "ECCELLENZA", "Regionali", "2026/2027", "A", "", false,
                                                190L),
                                        new AnagraficaPresenze.Squadra(
                                                "UNDER 17 REGIONALE", "Regionali", "2026/2027", null,
                                                "B", true, null))));

        assertThat(squadre.di(new Societa().setAnagraficaSocietaId(7L)))
                .containsExactly(
                        new SquadreSocieta.Squadra(
                                "ECCELLENZA", "Regionali", "2026/2027", "A", "", false, "DON ORIONE"),
                        new SquadreSocieta.Squadra(
                                "UNDER 17 REGIONALE", "Regionali", "2026/2027", null, "B", true, null));
    }
}
