package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;

import it.trovacampo.api.indirizzi.FonteCampiLnd.CampoLnd;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Righe vere della collection societa, come le scrive la sincronizzazione con presenze. */
class FonteCampiLndTest {

    private static final CampoLnd ROMULEA =
            new CampoLnd("SS ROMULEA", "ROMULEA", "VIA FARSALO 21", "ROMA", "RM", 41.88, 12.50, null);
    private static final CampoLnd ARNARA =
            new CampoLnd("A.S.D. ARNARA", "COMUNALE", "VIA MARITTIMA", "FROSINONE", "FR", 41.63, 13.35, "945001");
    private static final CampoLnd ROMULEA_ALTRA =
            new CampoLnd("ROMULEA", "COMUNALE", "VIA ROMA 1", "RIETI", "RI", null, null, null);
    private static final CampoLnd SENZA_POSIZIONE =
            new CampoLnd("SS ROMULEA", "ROMULEA B", "VIA FARSALO 23", "ROMA", "RM", null, null, null);

    @Test
    void stessoNomeNelloStessoComuneEConfidenzaAlta() {
        var trovato =
                FonteCampiLnd.cerca(
                        new SocietaDaLocalizzare("A.S.D. ROMULEA", "Roma (RM)"),
                        List.of(SENZA_POSIZIONE, ROMULEA, ARNARA));

        assertThat(trovato).hasValueSatisfying(indirizzo -> {
            assertThat(indirizzo.indirizzo()).isEqualTo("VIA FARSALO 21");
            assertThat(indirizzo.fonte()).isEqualTo(Fonte.CAMPI_LND);
            assertThat(indirizzo.confidenza()).isEqualTo(Confidenza.ALTA);
            // A parità di nome, il campo che ha già la posizione.
            assertThat(indirizzo.lat()).isEqualTo(41.88);
        });
    }

    @Test
    void ilCampoInUnAltroComuneDellaProvinciaSeLaSocietaEUnica() {
        var trovato =
                FonteCampiLnd.cerca(new SocietaDaLocalizzare("ARNARA", "ARNARA"), List.of(ROMULEA, ARNARA));

        assertThat(trovato).hasValueSatisfying(indirizzo -> {
            assertThat(indirizzo.comune()).isEqualTo("FROSINONE");
            assertThat(indirizzo.confidenza()).isEqualTo(Confidenza.MEDIA);
        });
    }

    @Test
    void nienteAltroComuneSeIlNomeEDiPiuSocieta() {
        // Una Romulea a Roma e una a Rieti: da Frascati non si sa quale.
        assertThat(
                        FonteCampiLnd.cerca(
                                new SocietaDaLocalizzare("ROMULEA", "FRASCATI"), List.of(ROMULEA, ROMULEA_ALTRA)))
                .isEmpty();
    }

    @Test
    void nienteAltroComuneInUnAltraProvincia() {
        assertThat(FonteCampiLnd.cerca(new SocietaDaLocalizzare("ARNARA", "LATINA"), List.of(ARNARA)))
                .isEmpty();
    }

    @Test
    void laMatricolaFigcVinceSulNome() {
        var trovato =
                FonteCampiLnd.cerca(
                        new SocietaDaLocalizzare("NOME DIVERSO NEL REGISTRO", "ARNARA", "01234567890", "0945001"),
                        List.of(ROMULEA, ARNARA));

        assertThat(trovato).hasValueSatisfying(indirizzo -> {
            assertThat(indirizzo.abbinatoA()).isEqualTo("A.S.D. ARNARA");
            assertThat(indirizzo.confidenza()).isEqualTo(Confidenza.ALTA);
        });
    }

    @Test
    void sottoLaSogliaNiente() {
        assertThat(
                        FonteCampiLnd.cerca(
                                new SocietaDaLocalizzare("A.S.D. ROMA CITY", "ROMA"), List.of(ROMULEA)))
                .isEmpty();
    }
}
