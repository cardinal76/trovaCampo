package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Impianti veri del censimento della Città metropolitana di Roma Capitale. */
class FonteImpiantiOpenDataTest {

    private static ImpiantoOpenData impianto(String nome, String comune, String indirizzo, String... sport) {
        return new ImpiantoOpenData(
                nome, "cmrc-censimento", "CC0 1.0", nome, comune, null, indirizzo, List.of(sport), null, null,
                null);
    }

    private static final ImpiantoOpenData KOLBE =
            impianto("Centro Sportivo Kolbe Ponte Mammolo", "ROMA", "Via Casal de' Pazzi, 16", "Calcio-11");
    private static final ImpiantoOpenData CERVETERI =
            impianto("Stadio Comunale \"Galli\"", "Cerveteri", "Via Settevene Palo", "Calcio-11");
    private static final ImpiantoOpenData CERVETERI_C5 =
            impianto("Palestra Comunale", "Cerveteri", "Via Fontana Morella", "Calcio-5");

    @Test
    void ilNomeDellImpiantoEQuelloDellaSocieta() {
        var trovato =
                FonteImpiantiOpenData.cerca(
                        new SocietaDaLocalizzare("C.S. KOLBE PONTE MAMMOLO A.S.D", "ROMA"), List.of(KOLBE));

        assertThat(trovato).hasValueSatisfying(indirizzo -> {
            assertThat(indirizzo.indirizzo()).isEqualTo("Via Casal de' Pazzi, 16");
            assertThat(indirizzo.fonte()).isEqualTo(FonteIndirizzo.OPEN_DATA);
            assertThat(indirizzo.confidenza()).isEqualTo(Confidenza.MEDIA);
            assertThat(indirizzo.haCoordinate()).isFalse();
            assertThat(indirizzo.attribuzione()).contains("CC0");
        });
    }

    @Test
    void laSocietaColNomeDelPaeseEIlSuoUnicoCampoA11() {
        var trovato =
                FonteImpiantiOpenData.cerca(
                        new SocietaDaLocalizzare("A.S.D. CERVETERI CALCIO", "CERVETERI"),
                        List.of(CERVETERI, CERVETERI_C5));

        assertThat(trovato).hasValueSatisfying(indirizzo -> {
            assertThat(indirizzo.impianto()).isEqualTo("Stadio Comunale \"Galli\"");
            assertThat(indirizzo.confidenza()).isEqualTo(Confidenza.BASSA);
        });
    }

    @Test
    void dueCampiA11NelPaeseNonSiSceglie() {
        ImpiantoOpenData altro = impianto("Campo Sportivo Valcanneto", "Cerveteri", "Via Valcanneto", "Calcio-11");

        assertThat(
                        FonteImpiantiOpenData.cerca(
                                new SocietaDaLocalizzare("A.S.D. CERVETERI", "CERVETERI"), List.of(CERVETERI, altro)))
                .isEmpty();
    }

    @Test
    void unaSocietaCheNonHaIlNomeDelPaeseNonPrendeIlComunale() {
        assertThat(
                        FonteImpiantiOpenData.cerca(
                                new SocietaDaLocalizzare("ATLETICO ETRURIA", "CERVETERI"), List.of(CERVETERI)))
                .isEmpty();
    }
}
