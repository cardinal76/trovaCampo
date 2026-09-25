package it.trovacampo.api.repository;

import it.trovacampo.api.dominio.Esclusione;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/** I campi di presenze eliminati da chi amministra, vedi {@link Esclusione}. */
public interface EsclusioniRepository extends MongoRepository<Esclusione, String> {

    /** Le più recenti prima: di solito si cerca quella appena fatta per errore. */
    List<Esclusione> findAllByOrderByEsclusaIlDesc();
}
