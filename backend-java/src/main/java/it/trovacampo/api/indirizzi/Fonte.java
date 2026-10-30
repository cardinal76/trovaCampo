package it.trovacampo.api.indirizzi;

/** Da dove viene un indirizzo, nell'ordine in cui le fonti si interrogano. */
public enum Fonte {
    /** I campi di gioco dei Comunicati Ufficiali LND, già in TrovaCampo (collection societa). */
    CAMPI_LND("Comunicati Ufficiali LND - Comitato Regionale Lazio, tramite l'anagrafica di presenze"),
    /** I censimenti degli impianti sportivi in open data (collection impianto_open_data). */
    OPEN_DATA("Censimento impianti sportivi di Roma e Provincia, Città metropolitana di Roma Capitale (CC0 1.0)"),
    /** La ricerca su OpenStreetMap, per nome e comune: l'ultima risorsa. */
    GEOCODING_OSM("© OpenStreetMap contributors, ODbL 1.0 (ricerca Nominatim)");

    private final String attribuzione;

    Fonte(String attribuzione) {
        this.attribuzione = attribuzione;
    }

    /** La riga da mostrare accanto al dato, come chiede la licenza della fonte. */
    public String attribuzione() {
        return attribuzione;
    }
}
