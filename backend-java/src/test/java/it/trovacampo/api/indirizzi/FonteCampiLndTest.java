package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.anagrafica.AnagraficaPresenze;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Le regole del collegamento ai campi LND, con nomi veri dei comunicati e del registro. */
class FonteCampiLndTest {

    private final SocietaRepository societa = mock(SocietaRepository.class);
    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final FonteCampiLnd fonte = new FonteCampiLnd(societa, anagrafica);

    private static Societa campo(String id, String nome, String indirizzo, String comune, Double lat) {
        return new Societa().setId(id).setNomeSocieta(nome).setNomeImpianto("COMUNALE")
                .setIndirizzoImpianto(indirizzo).setLocalitaImpianto(comune).setLat(lat).setLng(lat);
    }

    @Test
    void stesseParoleDistintiveEConfidenzaAlta() {
        when(societa.tuttiICampi()).thenReturn(List.of(campo("s1", "SS ROMULEA", "VIA FARSALO 21", "ROMA", 41.88)));
        when(anagrafica.impianti()).thenReturn(List.of());

        assertThat(fonte.cerca(new SocietaDaLocalizzare("ASSOCIAZIONE SPORTIVA DILETTANTISTICA ROMULEA", "Roma")))
                .hasValueSatisfying(trovato -> {
                    assertThat(trovato.indirizzo()).isEqualTo("VIA FARSALO 21");
                    assertThat(trovato.fonte()).isEqualTo(FonteIndirizzo.CAMPO_TROVACAMPO);
                    assertThat(trovato.confidenza()).isEqualTo(Confidenza.ALTA);
                    assertThat(trovato.regola()).isEqualTo("nome");
                    assertThat(trovato.societaTrovaCampoId()).isEqualTo("s1");
                });
    }

    @Test
    void unaSchedaSenzaPosizioneNonECandidata() {
        when(societa.tuttiICampi()).thenReturn(List.of(campo("s1", "SS ROMULEA", "VIA FARSALO 21", "ROMA", null)));
        when(anagrafica.impianti()).thenReturn(List.of());

        assertThat(fonte.cerca(new SocietaDaLocalizzare("ROMULEA", "Roma"))).isEmpty();
    }

    @Test
    void unaParolaSolaNelloStessoComuneEConfidenzaMedia() {
        when(societa.tuttiICampi())
                .thenReturn(List.of(campo("s1", "A.S.D. PRENESTINO", "VIA PRENESTINA 1", "ROMA", 41.9)));
        when(anagrafica.impianti()).thenReturn(List.of());

        assertThat(fonte.cerca(new SocietaDaLocalizzare("VIRTUS PRENESTINO", "Roma (RM)")))
                .hasValueSatisfying(trovato -> {
                    assertThat(trovato.regola()).isEqualTo("parola+comune");
                    assertThat(trovato.confidenza()).isEqualTo(Confidenza.MEDIA);
                });
    }

    @Test
    void iCampiSiLeggonoUnaVoltaFinoAllaRicarica() {
        when(societa.tuttiICampi()).thenReturn(List.of());
        when(anagrafica.impianti()).thenReturn(List.of());

        fonte.cerca(new SocietaDaLocalizzare("A", "ROMA"));
        fonte.cerca(new SocietaDaLocalizzare("B", "ROMA"));
        fonte.ricarica();
        fonte.cerca(new SocietaDaLocalizzare("C", "ROMA"));

        verify(societa, times(2)).tuttiICampi();
        verify(anagrafica, times(2)).impianti();
    }
}
