package it.trovacampo.api.societa;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** I campi di presenze eliminati da chi amministra, vedi {@link Esclusione}. */
public interface EsclusioniRepository extends MongoRepository<Esclusione, String> {

    /** Le più recenti prima: di solito si cerca quella appena fatta per errore. */
    List<Esclusione> findAllByOrderByEsclusaIlDesc();
}
