package it.trovacampo.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class ProvinceMancantiTest {

    private final SocietaRepository repository = mock(SocietaRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ProvinceMancanti province = new ProvinceMancanti(repository, mongo);

    private static Societa campo(String id, String localita, String provincia) {
        return new Societa().setId(id).setLocalitaImpianto(localita).setProvinciaImpianto(provincia);
    }

    @Test
    void scriveSoloLaProvinciaDeiCampiCheNeSonoSenza() {
        when(repository.findAll())
                .thenReturn(
                        List.of(
                                campo("1", "VITERBO", ""),
                                campo("2", "Roma", null),
                                // Già scritta, anche se diversa dal comune: resta.
                                campo("3", "Roma", "LT"),
                                // Comune sconosciuto: niente da scrivere.
                                campo("4", "Tor di Quinto", "")));

        province.run();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(query.capture(), update.capture(), eq(Societa.class));
        assertThat(query.getAllValues())
                .extracting(q -> q.getQueryObject().get("_id"))
                .containsExactly("1", "2");
        assertThat(update.getAllValues())
                .extracting(u -> u.getUpdateObject().get("$set", org.bson.Document.class).get("provinciaImpianto"))
                .containsExactly("VT", "RM");
        // Mai il documento intero: la geocodifica potrebbe star scrivendo le coordinate.
        verify(repository, never()).saveAll(any());
    }
}
