package it.trovacampo.api.repository;

import it.trovacampo.api.dominio.Societa;
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
     */
    @Query(
            value = "{}",
            fields =
                    "{ 'siglaSocieta': 1, 'nomeSocieta': 1, 'nomeImpianto': 1,"
                            + " 'indirizzoImpianto': 1, 'localitaImpianto': 1,"
                            + " 'provinciaImpianto': 1, 'lat': 1, 'lng': 1,"
                            + " 'posizioneApprossimata': 1 }",
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

    default Optional<Societa> primaDaGeocodificare(int versione) {
        return daGeocodificare(versione, PageRequest.of(0, 1)).stream().findFirst();
    }
}
