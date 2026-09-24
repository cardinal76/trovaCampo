package it.trovacampo.api.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProvinciaDalComuneTest {

    @Test
    void ilComuneScrittoComeCapitaDaLaSuaProvincia() {
        assertThat(ProvinciaDalComune.sigla("ROMA")).isEqualTo("RM");
        assertThat(ProvinciaDalComune.sigla("Latina")).isEqualTo("LT");
        assertThat(ProvinciaDalComune.sigla("SAN VITTORE DEL LAZIO")).isEqualTo("FR");
        assertThat(ProvinciaDalComune.sigla("ROCCA D'ARCE")).isEqualTo("FR");
        assertThat(ProvinciaDalComune.sigla("rieti")).isEqualTo("RI");
        assertThat(ProvinciaDalComune.sigla("Civita Castellana")).isEqualTo("VT");
    }

    @Test
    void laFrazioneNonConfonde() {
        assertThat(ProvinciaDalComune.sigla("Roma (Tuscolano)")).isEqualTo("RM");
        assertThat(ProvinciaDalComune.sigla("Ostia (Roma)")).isEqualTo("RM");
        assertThat(ProvinciaDalComune.sigla("GUIDONIA MONTECELIO (VILLALBA")).isEqualTo("RM");
        assertThat(ProvinciaDalComune.sigla("MONTEROTONDO SCALO")).isEqualTo("RM");
        assertThat(ProvinciaDalComune.sigla("LATINA SCALO")).isEqualTo("LT");
    }

    @Test
    void ilComuneAccorciatoSiRiconosceSeNonCiSonoDubbi() {
        assertThat(ProvinciaDalComune.sigla("CISTERNA")).isEqualTo("LT");
        // "San ..." sono tanti comuni: meglio nessuna provincia che una a caso.
        assertThat(ProvinciaDalComune.sigla("SAN")).isEmpty();
    }

    @Test
    void quelloCheNonSiRiconosceRestaSenzaProvincia() {
        assertThat(ProvinciaDalComune.sigla("TOR DI QUINTO")).isEmpty();
        assertThat(ProvinciaDalComune.sigla("Milano")).isEmpty();
        assertThat(ProvinciaDalComune.sigla("")).isEmpty();
        assertThat(ProvinciaDalComune.sigla(null)).isEmpty();
    }
}
