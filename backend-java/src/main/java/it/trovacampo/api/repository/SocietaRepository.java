package it.trovacampo.api.repository;

import it.trovacampo.api.dominio.Societa;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    /**
     * Tutti i campi, per l'elenco e la mappa completi. Solo i campi che
     * servono a quelle due pagine: con qualche migliaio di società l'anagrafica
     * e i campionati moltiplicherebbero il peso della risposta per niente.
     * {@code posizioneApprossimata} serve al filtro dell'elenco per chi
     * amministra, e c'è solo sulle poche società che ce l'hanno vera.
     * {@code anagraficaImpiantoId} è la chiave con cui la mappa trova le
     * prossime partite di ogni campo: senza, il popup e il filtro «solo campi
     * con partite» non ne troverebbero mai nessuna. {@code logoUrl} è lo
     * stemma accanto a ogni riga dell'elenco.
     */
    @Query(
            value = "{}",
            fields =
                    "{ 'siglaSocieta': 1, 'nomeSocieta': 1, 'nomeImpianto': 1,"
                            + " 'indirizzoImpianto': 1, 'localitaImpianto': 1,"
                            + " 'provinciaImpianto': 1, 'lat': 1, 'lng': 1,"
                            + " 'posizioneApprossimata': 1, 'anagraficaImpiantoId': 1,"
                            + " 'logoUrl': 1 }",
            sort = "{ 'nomeSocieta': 1, 'nomeImpianto': 1 }")
    List<Societa> tuttiICampi();

    /**
     * Una società senza coordinate su cui la versione corrente della
     * geocodifica non ha ancora rinunciato. {@code lat: null} comprende anche
     * il campo assente, e {@code $not $gte} anche una versione mai scritta:
     * così i campi rinunciati da una versione precedente tornano in coda.
     */
    @Query("{ 'lat': null, 'geocodificaFallitaVersione': { $not: { $gte: ?0 } } }")
    List<Societa> daGeocodificare(int versione, Pageable pagina);

    /**
     * Gli stemmi delle società di presenze con questi id, per le squadre delle
     * prossime partite: solo le righe che uno stemma ce l'hanno, e di ognuna
     * solo l'id e lo stemma. Una società con più campi ha più righe.
     */
    @Query(
            value = "{ 'anagraficaSocietaId': { $in: ?0 }, 'logoUrl': { $ne: null } }",
            fields = "{ 'anagraficaSocietaId': 1, 'logoUrl': 1 }")
    List<Societa> stemmiDi(Collection<Long> anagraficaSocietaIds);

    /** I campi che vengono da questi impianti di presenze: per le notifiche delle partite. */
    List<Societa> findByAnagraficaImpiantoIdIn(Collection<Long> impianti);

    default Optional<Societa> primaDaGeocodificare(int versione) {
        return daGeocodificare(versione, PageRequest.of(0, 1)).stream().findFirst();
    }
}
