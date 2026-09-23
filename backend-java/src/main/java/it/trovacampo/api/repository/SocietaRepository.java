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
     * Una società senza coordinate su cui la geocodifica non ha ancora
     * rinunciato. {@code lat: null} comprende anche il campo assente.
     */
    @Query("{ 'lat': null, 'geocodificaFallita': { $ne: true } }")
    List<Societa> daGeocodificare(Pageable pagina);

    default Optional<Societa> primaDaGeocodificare() {
        return daGeocodificare(PageRequest.of(0, 1)).stream().findFirst();
    }
}
