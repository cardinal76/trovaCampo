package it.trovacampo.api.geocoding;

import java.util.Optional;

/** Traduce un indirizzo nelle sue coordinate. */
public interface Geocoding {

    record Coordinate(double lat, double lng) {}

    /**
     * Vuoto se l'indirizzo non è stato riconosciuto: riprovare darebbe lo
     * stesso esito.
     *
     * @throws GeocodingNonDisponibile se il servizio non risponde (rete,
     *     timeout, 5xx, 429): non vuol dire che l'indirizzo non esista, e va
     *     riprovato piu' tardi
     */
    Optional<Coordinate> geocodifica(String indirizzo);
}
