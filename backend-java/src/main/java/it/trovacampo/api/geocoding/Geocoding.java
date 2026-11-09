package it.trovacampo.api.geocoding;

import java.util.Optional;

/** Traduce un indirizzo nelle sue coordinate. */
public interface Geocoding {

    record Coordinate(double lat, double lng) {}

    /** Vuoto se l'indirizzo non è stato riconosciuto o il servizio non risponde. */
    Optional<Coordinate> geocodifica(String indirizzo);
}
