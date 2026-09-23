package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import java.util.List;
import org.junit.jupiter.api.Test;

class SquadreSocietaTest {

    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final SquadreSocieta squadre = new SquadreSocieta(anagrafica);

    @Test
    void unaSocietaCheNonVieneDaPresenzeNonHaSquadre() {
        assertThat(squadre.di(new Societa().setNomeSocieta("Certosa Calcio"))).isEmpty();
        verify(anagrafica, never()).scheda(anyLong());
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
