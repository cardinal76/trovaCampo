package it.trovacampo.api.indirizzi;

/**
 * Quanto ci si può fidare di un indirizzo trovato.
 *
 * <ul>
 *   <li>ALTA: stesse parole distintive di una società LND (regole
 *       {@code nome} e {@code nome+comune} di {@link FonteCampiLnd});
 *   <li>MEDIA: le parole dell'una contenute nell'altra (regole sulle parole),
 *       o nome dell'impianto open data che corrisponde alla società da 0,9;
 *   <li>BASSA: tutto quello che è un'ipotesi: un impianto open data poco
 *       simile, l'unico campo a 11 del paese, un campo trovato su OpenStreetMap.
 * </ul>
 */
public enum Confidenza {
    ALTA,
    MEDIA,
    BASSA
}
