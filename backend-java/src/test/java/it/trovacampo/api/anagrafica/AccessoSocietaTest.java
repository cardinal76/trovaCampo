package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.societa.Societa;
import it.trovacampo.api.societa.SocietaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class AccessoSocietaTest {

    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final SocietaRepository repository = mock(SocietaRepository.class);
    private final AccessoSocieta accesso =
            new AccessoSocieta(anagrafica, new SocietaInPresenze(anagrafica, repository));

    private static Societa boreale() {
        return new Societa().setId("m1").setNomeSocieta("BOREALE").setAnagraficaSocietaId(7L);
    }

    private static AnagraficaPresenze.Scheda scheda(AnagraficaPresenze.Accesso accesso) {
        return new AnagraficaPresenze.Scheda(7L, "BOREALE", List.of(), List.of(), accesso);
    }

    @Test
    void unClubClienteHaIlSuoAccesso() {
        var atteso = new AnagraficaPresenze.Accesso("boreale", "/api/organizzazioni/3/logo");
        when(anagrafica.scheda(7)).thenReturn(scheda(atteso));

        assertThat(accesso.di(boreale())).contains(atteso);
    }

    @Test
    void unaSocietaNonClienteNonHaAccesso() {
        when(anagrafica.scheda(7)).thenReturn(scheda(null));

        assertThat(accesso.di(boreale())).isEmpty();
    }

    /** Un presenze che non manda ancora il campo: nessun errore, nessun collegamento. */
    @Test
    void unAliasVuotoValeComeAssenza() {
        when(anagrafica.scheda(7)).thenReturn(scheda(new AnagraficaPresenze.Accesso(" ", null)));

        assertThat(accesso.di(boreale())).isEmpty();
    }

    @Test
    void unaSocietaCheInPresenzeNonCeNonChiedeLaScheda() {
        when(anagrafica.cerca("Certosa Calcio")).thenReturn(List.of());

        assertThat(accesso.di(new Societa().setId("m2").setNomeSocieta("Certosa Calcio"))).isEmpty();
        verify(anagrafica, never()).scheda(org.mockito.ArgumentMatchers.anyLong());
    }

    /** Presenze giu': e' un collegamento in meno, non una scheda che si rompe. */
    @Test
    void presenzeGiuNonRompeLaScheda() {
        when(anagrafica.scheda(7)).thenThrow(new ResourceAccessException("Connection refused"));

        assertThat(accesso.di(boreale())).isEmpty();
    }
}
