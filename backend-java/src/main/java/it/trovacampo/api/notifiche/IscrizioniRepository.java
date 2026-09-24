package it.trovacampo.api.notifiche;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface IscrizioniRepository extends MongoRepository<Iscrizione, String> {

    /** Quelle con almeno un avviso acceso: le altre non hanno niente da ricevere. */
    @Query("{ $or: [ { 'avvisoSquadre': true }, { 'avvisoVicino': true } ] }")
    List<Iscrizione> conAvvisiAccesi();

    @Query(value = "{ $or: [ { 'avvisoSquadre': true }, { 'avvisoVicino': true } ] }", exists = true)
    boolean esisteConAvvisiAccesi();
}
