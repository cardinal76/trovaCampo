package it.trovacampo.api.indirizzi;

/**
 * Da dove viene l'indirizzo del campo di una società, nell'ordine di
 * preferenza: il campo di gioco dai comunicati LND prima di tutto, la
 * ricerca su OpenStreetMap per ultima.
 *
 * <p>I nomi dei valori sono salvati nei documenti {@code prospect} e letti
 * dall'app ({@code modelli/prospect.ts}): non si rinominano.
 */
public enum FonteIndirizzo {
    /** Il campo di una scheda di TrovaCampo (comunicati LND, file importati, correzioni a mano). */
    CAMPO_TROVACAMPO("Comunicati Ufficiali LND - Comitato Regionale Lazio, tramite TrovaCampo"),
    /** Il campo dell'anagrafica di presenze, letto dai Comunicati Ufficiali del Lazio. */
    CAMPO_PRESENZE("Comunicati Ufficiali LND - Comitato Regionale Lazio, tramite l'anagrafica di presenze"),
    /**
     * Un censimento degli impianti in open data (collection impianto_open_data):
     * l'attribuzione del dataset preciso è quella di {@link ImpiantoOpenData#attribuzioneDaMostrare()}.
     */
    OPEN_DATA("Censimenti open data degli impianti sportivi (Città metropolitana di Roma Capitale, CC0 1.0;"
            + " comuni del Veneto su dati.veneto.it, CC BY 4.0)"),
    /** Un campo trovato su OpenStreetMap per nome e comune: posizione indicativa. */
    GEOCODIFICA("© OpenStreetMap contributors, ODbL 1.0 (ricerca Nominatim)");

    private final String attribuzione;

    FonteIndirizzo(String attribuzione) {
        this.attribuzione = attribuzione;
    }

    /** La riga da mostrare accanto al dato, come chiede la licenza della fonte. */
    public String attribuzione() {
        return attribuzione;
    }
}
