package it.trovacampo.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.repository.Query;

/**
 * La proiezione di {@link SocietaRepository#tuttiICampi()}, che elenco e mappa
 * scaricano per intero.
 *
 * <p>Un campo che manca dalla proiezione non da' errori: arriva al telefono
 * come assente, e la funzione che lo usa smette di funzionare in silenzio. E'
 * successo con {@code anagraficaImpiantoId}, senza il quale la mappa non
 * abbinava nessuna partita al suo campo.
 */
class SocietaRepositoryTest {

    @Test
    void tuttiICampiPortanoQuelloCheServeAElencoEMappa() throws Exception {
        Query query = SocietaRepository.class.getMethod("tuttiICampi").getAnnotation(Query.class);

        assertThat(query.fields())
                .contains("'lat': 1", "'lng': 1", "'provinciaImpianto': 1",
                        "'posizioneApprossimata': 1", "'anagraficaImpiantoId': 1", "'logoUrl': 1");
    }
}
