package it.trovacampo.api.indirizzi;

/**
 * Il miglior indirizzo trovato per una società.
 *
 * @param indirizzo via e civico del campo, come li scrive la fonte
 * @param comune il comune del campo, che può non essere quello della sede
 * @param lat nulla quando la fonte dà l'indirizzo ma non la posizione e il
 *     geocoding non l'ha riconosciuto (o non è stato chiesto)
 * @param lng come lat
 * @param punteggio quanto si somigliano i nomi, da 0 a 1 (1 per le regole
 *     esatte dei campi LND)
 * @param abbinatoA il nome con cui la società è stata riconosciuta nella
 *     fonte: la società LND, l'impianto open data o il luogo OSM
 * @param impianto il nome del campo, quando la fonte lo dà
 * @param attribuzione la riga di attribuzione dovuta alla fonte (e a OSM,
 *     se le coordinate vengono dalla geocodifica)
 * @param regola come si è trovato: per i campi LND la regola del
 *     collegamento ({@code nome}, {@code nome+comune}, {@code parole},
 *     {@code parole+comune}, {@code parola+comune}), poi {@code open-data},
 *     {@code open-data+paese}, {@code osm-nome}, {@code osm-paese}
 * @param societaTrovaCampoId la scheda di TrovaCampo da cui viene il campo
 * @param anagraficaImpiantoId l'impianto di presenze da cui viene il campo
 */
public record IndirizzoMigliore(
        String indirizzo,
        String comune,
        Double lat,
        Double lng,
        FonteIndirizzo fonte,
        Confidenza confidenza,
        double punteggio,
        String abbinatoA,
        String impianto,
        String attribuzione,
        String regola,
        String societaTrovaCampoId,
        Long anagraficaImpiantoId) {

    /** Un indirizzo che non viene da un campo LND: niente scheda né impianto di presenze. */
    public IndirizzoMigliore(
            String indirizzo,
            String comune,
            Double lat,
            Double lng,
            FonteIndirizzo fonte,
            Confidenza confidenza,
            double punteggio,
            String abbinatoA,
            String impianto,
            String regola) {
        this(indirizzo, comune, lat, lng, fonte, confidenza, punteggio, abbinatoA, impianto,
                fonte.attribuzione(), regola, null, null);
    }

    public boolean haCoordinate() {
        return lat != null && lng != null;
    }

    IndirizzoMigliore conCoordinate(double lat, double lng, String attribuzioneAggiunta) {
        return new IndirizzoMigliore(
                indirizzo, comune, lat, lng, fonte, confidenza, punteggio, abbinatoA, impianto,
                attribuzione + "; " + attribuzioneAggiunta, regola, societaTrovaCampoId, anagraficaImpiantoId);
    }
}
