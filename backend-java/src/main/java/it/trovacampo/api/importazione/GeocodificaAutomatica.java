package it.trovacampo.api.importazione;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.service.Geocoding;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dà le coordinate alle società che non le hanno, una alla volta.
 *
 * <p>Un file Excel può portare migliaia di righe senza latitudine e longitudine,
 * e Nominatim ne accetta al massimo una al secondo: geocodificarle durante il
 * caricamento vorrebbe dire una richiesta HTTP che dura ore. Qui invece il
 * caricamento finisce subito e le coordinate arrivano col tempo, al ritmo che
 * la usage policy di Nominatim consente.
 *
 * <p>Un indirizzo non riconosciuto viene segnato e non ritentato, altrimenti la
 * coda resterebbe per sempre bloccata sulla stessa società.
 */
@Component
@ConditionalOnProperty(name = "trovacampo.geocodifica.automatica", havingValue = "true")
public class GeocodificaAutomatica {

    private static final Logger log = LoggerFactory.getLogger(GeocodificaAutomatica.class);

    private final SocietaRepository repository;
    private final Geocoding geocoding;

    public GeocodificaAutomatica(SocietaRepository repository, Geocoding geocoding) {
        this.repository = repository;
        this.geocoding = geocoding;
    }

    /** fixedDelay e non fixedRate: l'attesa conta da quando Nominatim ha risposto. */
    @Scheduled(fixedDelayString = "${trovacampo.geocodifica.intervallo:PT1.1S}")
    public void geocodificaLaProssima() {
        try {
            repository.primaDaGeocodificare().ifPresent(this::geocodifica);
        } catch (RuntimeException eccezione) {
            // Mongo giù per un attimo non deve fermare lo scheduler per sempre.
            log.warn("Geocodifica automatica non riuscita: {}", eccezione.toString());
        }
    }

    void geocodifica(Societa societa) {
        String indirizzo = indirizzoCompleto(societa);
        geocoding
                .geocodifica(indirizzo)
                .ifPresentOrElse(
                        coordinate ->
                                societa.setLat(coordinate.lat())
                                        .setLng(coordinate.lng())
                                        .setGeocodificaFallita(null),
                        () -> {
                            log.info("Indirizzo non riconosciuto: '{}'", indirizzo);
                            societa.setGeocodificaFallita(true);
                        });
        repository.save(societa);
    }

    /**
     * "Via della Certosa 12, Roma RM, Italia": senza comune e paese Nominatim
     * sceglie la prima via con quel nome, spesso in un'altra regione.
     */
    static String indirizzoCompleto(Societa societa) {
        String localita =
                Stream.of(societa.getLocalitaImpianto(), societa.getProvinciaImpianto())
                        .filter(parte -> parte != null && !parte.isBlank())
                        .collect(Collectors.joining(" "));

        return Stream.of(societa.getIndirizzoImpianto(), localita, "Italia")
                .filter(parte -> parte != null && !parte.isBlank())
                .collect(Collectors.joining(", "));
    }
}
