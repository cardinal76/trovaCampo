package it.trovacampo.api.prospect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Al massimo una richiesta al secondo, con un orologio finto: nessun test dorme davvero. */
class LimitatoreRichiesteTest {

    private long adesso = 5_000_000_000L;
    private final List<Long> attese = new ArrayList<>();

    private LimitatoreRichieste limitatore(Duration intervallo) {
        return new LimitatoreRichieste(
                intervallo,
                () -> adesso,
                millis -> {
                    attese.add(millis);
                    adesso += millis * 1_000_000;
                });
    }

    @Test
    void laPrimaRichiestaPassaSubitoLeAltreAspettanoUnSecondo() throws Exception {
        LimitatoreRichieste limitatore = limitatore(Duration.ofSeconds(1));

        limitatore.prima();
        limitatore.prima();
        limitatore.prima();

        assertThat(attese).containsExactly(1000L, 1000L);
    }

    @Test
    void ilTempoGiaPassatoSiScontaDallAttesa() throws Exception {
        LimitatoreRichieste limitatore = limitatore(Duration.ofSeconds(1));

        limitatore.prima();
        adesso += 700_000_000L; // la risposta ha messo 0,7 secondi
        limitatore.prima();
        adesso += 2_500_000_000L; // una pagina lenta: nessuna attesa
        limitatore.prima();

        assertThat(attese).containsExactly(300L);
    }

    @Test
    void sottoIlSecondoNonSiScendeNemmenoConfigurandolo() throws Exception {
        LimitatoreRichieste limitatore = limitatore(Duration.ofMillis(200));

        limitatore.prima();
        limitatore.prima();

        assertThat(limitatore.intervalloMillis()).isEqualTo(1000L);
        assertThat(attese).containsExactly(1000L);
    }
}
