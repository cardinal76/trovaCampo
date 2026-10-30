package it.trovacampo.api.indirizzi;

/**
 * Quanto ci si può fidare di un indirizzo trovato.
 *
 * <ul>
 *   <li>ALTA: stessa matricola FIGC, o nome quasi identico a una società LND
 *       che gioca in casa nello stesso comune;
 *   <li>MEDIA: nome simile sopra la soglia, o identico a una società LND unica
 *       in tutta la regione ma con il campo in un altro comune della provincia,
 *       o nome dell'impianto open data che corrisponde alla società;
 *   <li>BASSA: tutto quello che è un'ipotesi: l'unico campo comunale del
 *       paese, o un campo trovato su OpenStreetMap.
 * </ul>
 */
public enum Confidenza {
    ALTA,
    MEDIA,
    BASSA
}
