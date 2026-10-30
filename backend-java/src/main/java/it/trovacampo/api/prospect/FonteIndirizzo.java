package it.trovacampo.api.prospect;

/**
 * Da dove viene l'indirizzo di un prospect, nell'ordine di preferenza della
 * card: il campo di gioco dai comunicati LND prima di tutto.
 */
public enum FonteIndirizzo {
    /** Il campo di una scheda di TrovaCampo (comunicati LND, file importati, correzioni a mano). */
    CAMPO_TROVACAMPO,
    /** Il campo dell'anagrafica di presenze, letto dai Comunicati Ufficiali del Lazio. */
    CAMPO_PRESENZE,
    /** Un censimento degli impianti in open data (prossime consegne). */
    OPEN_DATA,
    /** La geocodifica di denominazione e comune (prossime consegne): posizione indicativa. */
    GEOCODIFICA
}
