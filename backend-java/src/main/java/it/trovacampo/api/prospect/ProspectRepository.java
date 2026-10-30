package it.trovacampo.api.prospect;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ProspectRepository extends MongoRepository<Prospect, String> {

    /** Quelli con una posizione, per la mappa dell'amministrazione. */
    @Query("{ 'lat': { $ne: null }, 'lng': { $ne: null } }")
    List<Prospect> conCoordinate();

    /** Le società di calcio di una regione: il Lazio, per il collegamento ai campi. */
    List<Prospect> findByRegioneAndCalcioTrue(String regione);

    /** Tutti, uno alla volta, per l'export: a livello nazionale sono centomila righe. */
    @Query(value = "{}", sort = "{ 'regione': 1, 'comune': 1, 'denominazione': 1 }")
    Stream<Prospect> tuttiInOrdine();
}
