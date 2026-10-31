package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Impianti veri del censimento della Città metropolitana di Roma Capitale, e uno del Veneto. */
class FonteImpiantiOpenDataTest {

    private static ImpiantoOpenData impianto(String nome, String comune, String indirizzo, String... sport) {
        return new ImpiantoOpenData(
                nome, "cmrc-censimento", "CC0 1.0", nome, comune, null, indirizzo, List.of(sport), null, null,
                null, null);
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

    @Test
    void unImpiantoDelVenetoPortaLaSuaAttribuzioneELeSueCoordinate() {
        ImpiantoOpenData poleo =
                new ImpiantoOpenData(
                        "v", "veneto-comuni", "CC BY 4.0", "Impianto Calcio di Poleo", "Schio",
                        null, "Via Grumetto 104", List.of("Calcio-11"), 45.727, 11.3296, null,
                        "Comune di Schio - Impianti Sportivi, dati.veneto.it (CC BY 4.0)");

        var trovato =
                FonteImpiantiOpenData.cerca(new SocietaDaLocalizzare("A.S.D. CALCIO POLEO", "Schio (VI)"), List.of(poleo));

        assertThat(trovato).hasValueSatisfying(indirizzo -> {
            assertThat(indirizzo.indirizzo()).isEqualTo("Via Grumetto 104");
            assertThat(indirizzo.attribuzione())
                    .isEqualTo("Comune di Schio - Impianti Sportivi, dati.veneto.it (CC BY 4.0)");
            assertThat(indirizzo.lat()).isEqualTo(45.727);
            assertThat(indirizzo.lng()).isEqualTo(11.3296);
        });
    }

    @Test
    void unImpiantoImportatoPrimaDellAttribuzioneUsaQuellaDellaFonte() {
        assertThat(KOLBE.attribuzioneDaMostrare())
                .isEqualTo("Censimento impianti sportivi di Roma e Provincia, Città metropolitana di Roma Capitale"
                        + " (CC0 1.0)");
    }
}
