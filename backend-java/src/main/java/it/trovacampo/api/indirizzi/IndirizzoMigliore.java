package it.trovacampo.api.indirizzi;

/**
 * Il miglior indirizzo trovato per una società.
 *
 * @param indirizzo via e civico del campo, come li scrive la fonte
 * @param comune il comune del campo, che può non essere quello della sede
 * @param lat nulla quando la fonte dà l'indirizzo ma non la posizione e il
 *     geocoding non l'ha riconosciuto (o non è stato chiesto)
 * @param lng come lat
 * @param punteggio la somiglianza dei nomi, da 0 a 1 (1 anche per la
 *     matricola FIGC)
 * @param abbinatoA il nome con cui la società è stata riconosciuta nella
 *     fonte: la società LND, l'impianto open data o il luogo OSM
 * @param impianto il nome del campo, quando la fonte lo dà
 * @param attribuzione la riga di attribuzione dovuta alla fonte (e a OSM,
 *     se le coordinate vengono dalla geocodifica)
 */
public record IndirizzoMigliore(
        String indirizzo,
        String comune,
        Double lat,
        Double lng,
        Fonte fonte,
        Confidenza confidenza,
        double punteggio,
        String abbinatoA,
        String impianto,
        String attribuzione) {

    public boolean haCoordinate() {
        return lat != null && lng != null;
    }

    IndirizzoMigliore conCoordinate(double lat, double lng, String attribuzioneAggiunta) {
        return new IndirizzoMigliore(
                indirizzo, comune, lat, lng, fonte, confidenza, punteggio, abbinatoA, impianto,
                attribuzione + "; " + attribuzioneAggiunta);
    }
}
