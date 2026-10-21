package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.trovacampo.api.anagrafica.AnagraficaPresenze.Campo;
import it.trovacampo.api.anagrafica.AnagraficaPresenze.SocietaConCampo;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Le società di presenze che in TrovaCampo mancano perché non hanno un campo. */
class SocietaSenzaCampoTest {

    private final AnagraficaPresenze presenze = mock(AnagraficaPresenze.class);
    private final SocietaRepository repository = mock(SocietaRepository.class);
    private final SocietaSenzaCampo senzaCampo = new SocietaSenzaCampo(presenze, repository);

    @Test
    void tieneSoloQuelleSenzaCampoESenzaScheda() {
        when(presenze.societa())
                .thenReturn(List.of(
                        new SocietaConCampo(383L, "ASDPOL ZAGAROLO CALCIO", null, "https://play.lnd.it/z.png"),
                        new SocietaConCampo(1L, "CON CAMPO", new Campo(5L, "Comunale", null, null), null),
                        new SocietaConCampo(2L, "GIA COLLEGATA", null, null),
                        new SocietaConCampo(3L, "Aggiunta A Mano", null, null),
                        new SocietaConCampo(4L, "ATLETICO CÈRVETERI", null, null)));
        when(repository.nomiECollegamenti())
                .thenReturn(List.of(
                        new Societa().setNomeSocieta("Qualcosa").setAnagraficaSocietaId(2L),
                        new Societa().setNomeSocieta("aggiunta a mano")));

        assertThat(senzaCampo.elenco())
                .extracting(SocietaSenzaCampo.Voce::anagraficaSocietaId)
                .containsExactly(383L, 4L);
        assertThat(senzaCampo.elenco().get(0).logoUrl()).isEqualTo("https://play.lnd.it/z.png");
    }
}
