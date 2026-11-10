package it.trovacampo.api.segnalazioni;

import java.time.Instant;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SegnalazioniRepository extends MongoRepository<Segnalazione, String> {

    List<Segnalazione> findAllByOrderByRicevutaIlDesc();

    long countByRicevutaIlAfter(Instant dopo);
}
