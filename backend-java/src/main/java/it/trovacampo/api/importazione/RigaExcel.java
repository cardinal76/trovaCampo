package it.trovacampo.api.importazione;

/**
 * Una riga valida del foglio. Località e provincia sono vuote quando il file
 * non ha quelle colonne; le coordinate sono nulle quando mancano, e allora ci
 * pensa la geocodifica automatica.
 */
record RigaExcel(
        int numero,
        String nomeSocieta,
        String nomeImpianto,
        String indirizzo,
        String localita,
        String provincia,
        Double lat,
        Double lng) {}
