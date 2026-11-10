/** Lo stato del caricamento di una sezione della scheda. */
export type Stato = 'caricamento' | 'completata' | 'errore';

/**
 * Lo stato della sezione degli ultimi risultati. Ha due stati in più delle
 * altre perché deve raccontare due cose in più:
 *
 * - `chiusa`: nessuno l'ha ancora aperta, e i risultati non sono stati
 *   chiesti. Si chiedono all'apertura della piega e non all'arrivo sulla
 *   scheda: questa è la pagina di un campo, e la maggior parte di chi la apre
 *   vuole sapere dove si gioca, non com'è finita domenica;
 * - `assenti`: presenze non ha i risultati di questa società. Può essere un
 *   campo che lì non c'è, o un presenze che non ha ancora l'endpoint, perché
 *   TrovaCampo si rilascia per conto suo e questa scheda può arrivare sul
 *   server prima. Riprovare non serve, e la sezione non lo promette.
 */
export type StatoSezioneRisultati = 'chiusa' | 'caricamento' | 'completata' | 'assenti' | 'errore';
