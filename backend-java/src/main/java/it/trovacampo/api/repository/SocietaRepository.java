package it.trovacampo.api.repository;

import it.trovacampo.api.dominio.Societa;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SocietaRepository extends MongoRepository<Societa, String> {

    /**
     * Cerca nel testo normalizzato (nome società, impianto, indirizzo e
     * località). Il parametro è una regex già normalizzata e con i
     * metacaratteri neutralizzati, vedi {@link it.trovacampo.api.dominio.Testo}.
     */
    @Query("{ 'testoRicerca': { $regex: ?0 } }")
    List<Societa> cercaPerTesto(String regexNormalizzata);
}
