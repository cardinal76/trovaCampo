package it.trovacampo.api.notifiche;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * La chiave di una squadra seguita: la calcolano il backend (nelle squadre
 * della scheda) e il pianificatore (nelle partite), e devono coincidere anche
 * quando i due comunicati scrivono il campionato in modo diverso.
 */
class ChiaveSquadraTest {

    @Test
    void societaCampionatoEnteELettera() {
        assertThat(ChiaveSquadra.di(412L, "ECCELLENZA", "Regionali", "")).isEqualTo("412|eccellenza|regionali|");
        assertThat(ChiaveSquadra.di(412L, "Juniores Under 19 - Regionale", "Regionali", "B"))
                .isEqualTo("412|juniores under 19 regionale|regionali|b");
        assertThat(ChiaveSquadra.di(412L, "Terza Categoria", "Delegazione di Roma", null))
                .isEqualTo("412|terza categoria|delegazione di roma|");
        assertThat(ChiaveSquadra.di(null, "ECCELLENZA", "Regionali", "")).isNull();
    }

    @Test
    void laChiaveHaLaFormaCheIlServerAccetta() {
        assertThat(ChiaveSquadra.di(412L, "Promozione – Girone “C”", "Città di Roma", "B"))
                .matches(ChiaveSquadra.FORMA);
    }

    @Test
    void seguitaConOSenzaLettera() {
        List<String> seguite = List.of("412|eccellenza|regionali|b");

        assertThat(ChiaveSquadra.seguita(seguite, 412L, "ECCELLENZA", "Regionali", "B")).isTrue();
        assertThat(ChiaveSquadra.seguita(seguite, 412L, "ECCELLENZA", "Regionali", "")).isFalse();
        // Un presenze che la lettera non la manda: basta la società nel campionato.
        assertThat(ChiaveSquadra.seguita(seguite, 412L, "ECCELLENZA", "Regionali", null)).isTrue();
        assertThat(ChiaveSquadra.seguita(seguite, 41L, "ECCELLENZA", "Regionali", null)).isFalse();
        assertThat(ChiaveSquadra.seguita(seguite, null, "ECCELLENZA", "Regionali", "B")).isFalse();
    }
}
