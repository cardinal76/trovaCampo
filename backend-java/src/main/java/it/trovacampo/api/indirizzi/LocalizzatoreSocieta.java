package it.trovacampo.api.indirizzi;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.importazione.GeocodificaAutomatica;
import it.trovacampo.api.service.Geocoding;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Il miglior indirizzo del campo di una società, dalle fonti in ordine di
 * affidabilità: i campi LND che TrovaCampo ha già, i censimenti degli
 * impianti in open data, una ricerca su OpenStreetMap. Si ferma alla prima
 * che risponde.
 *
 * <p>È il punto d'ingresso per chi arricchisce le società del registro RASD
 * (la collection {@code prospect}): {@link #trova(SocietaDaLocalizzare)}.
 *
 * <p>Un indirizzo senza posizione (il censimento della Città metropolitana
 * non ha coordinate) si geocodifica con il geocoder di TrovaCampo, con la
 * stessa pulizia degli indirizzi dei comunicati.
 */
@Service
public class LocalizzatoreSocieta {

    static final String ATTRIBUZIONE_COORDINATE = "coordinate © OpenStreetMap contributors, ODbL 1.0 (Nominatim)";

    private final FonteCampiLnd campiLnd;
    private final FonteImpiantiOpenData impiantiOpenData;
    private final RicercaCampiOsm osm;
    private final Geocoding geocoding;

    public LocalizzatoreSocieta(
            FonteCampiLnd campiLnd,
            FonteImpiantiOpenData impiantiOpenData,
            RicercaCampiOsm osm,
            Geocoding geocoding) {
        this.campiLnd = campiLnd;
        this.impiantiOpenData = impiantiOpenData;
        this.osm = osm;
        this.geocoding = geocoding;
    }

    /**
     * Cosa può fare una ricerca.
     *
     * @param usaRete false per restare sui dati già in Mongo: niente
     *     geocodifica degli indirizzi e niente ricerca su OSM. Una ricerca con
     *     la rete fa al massimo tre richieste a Nominatim, una al secondo.
     */
    public record Opzioni(boolean usaRete) {
        public static final Opzioni COMPLETA = new Opzioni(true);
        public static final Opzioni SENZA_RETE = new Opzioni(false);
    }

    /** Con tutte le fonti, OpenStreetMap compreso. */
    public Optional<IndirizzoMigliore> trova(SocietaDaLocalizzare societa) {
        return trova(societa, Opzioni.COMPLETA);
    }

    public Optional<IndirizzoMigliore> trova(SocietaDaLocalizzare societa, Opzioni opzioni) {
        if (societa == null || societa.denominazione() == null || societa.denominazione().isBlank()) {
            return Optional.empty();
        }
        Optional<IndirizzoMigliore> trovato =
                campiLnd.cerca(societa).or(() -> impiantiOpenData.cerca(societa));
        if (trovato.isPresent()) {
            return Optional.of(opzioni.usaRete() ? conCoordinate(trovato.get()) : trovato.get());
        }
        return opzioni.usaRete() ? osm.cerca(societa) : Optional.empty();
    }

    /** L'indirizzo con la posizione, se manca e il geocoder la trova. */
    private IndirizzoMigliore conCoordinate(IndirizzoMigliore indirizzo) {
        if (indirizzo.haCoordinate()) {
            return indirizzo;
        }
        Societa daGeocodificare =
                new Societa().setIndirizzoImpianto(indirizzo.indirizzo()).setLocalitaImpianto(indirizzo.comune());
        for (String tentativo : GeocodificaAutomatica.tentativi(daGeocodificare)) {
            Optional<Geocoding.Coordinate> coordinate = geocoding.geocodifica(tentativo);
            if (coordinate.isPresent()) {
                return indirizzo.conCoordinate(
                        coordinate.get().lat(), coordinate.get().lng(), ATTRIBUZIONE_COORDINATE);
            }
        }
        return indirizzo;
    }
}
