package it.trovacampo.api.importazione;

/**
 * Una riga valida del foglio. Località e provincia sono vuote quando il file
 * non ha quelle colonne; le coordinate sono nulle quando mancano, e allora ci
 * pensa la geocodifica automatica.
 *
 * <p>{@code anagraficaSocietaId} c'è solo per le righe che arrivano
 * dall'anagrafica di presenze: è la società da cui leggere squadre e
 * campionati. {@code anagraficaImpiantoId}, con lei, è il campo in presenze:
 * la chiave per abbinargli le partite in calendario. {@code logoUrl} è lo
 * stemma che presenze ha per quella società, se ne ha uno.
 */
record RigaExcel(
        int numero,
        String nomeSocieta,
        String nomeImpianto,
        String indirizzo,
        String localita,
        String provincia,
        Double lat,
        Double lng,
        Long anagraficaSocietaId,
        Long anagraficaImpiantoId,
        String logoUrl) {

    RigaExcel(
            int numero,
            String nomeSocieta,
            String nomeImpianto,
            String indirizzo,
            String localita,
            String provincia,
            Double lat,
            Double lng) {
        this(numero, nomeSocieta, nomeImpianto, indirizzo, localita, provincia, lat, lng, null, null, null);
    }
}
