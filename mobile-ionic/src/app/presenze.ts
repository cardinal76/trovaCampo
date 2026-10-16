/**
 * Le pagine pubbliche del Comitato su presenze, dove TrovaCampo manda chi
 * vuole vederci dentro.
 *
 * Campionati, gironi, classifiche e calendari li legge presenze dai comunicati
 * ufficiali, e li pubblica senza chiedere login. TrovaCampo quelle pagine non
 * le rifà: di una partita mostra il nome del campionato e la lettera del
 * girone, che bastano a riconoscerla, e per il resto — la classifica, le altre
 * giornate, chi gioca con chi — porta là.
 *
 * Sta in un file suo per la stessa ragione di {@link SITO_FOOTBALLER}: i
 * collegamenti sono più d'uno e devono portare nello stesso posto, e se un
 * giorno l'indirizzo cambia si cambia qui.
 */
const COMITATO_PUBBLICO = 'https://presenze.footballer.it/comitato/lazio';

/** La pagina di un campionato: i suoi gironi, e l'organico di ognuno. */
export function paginaCampionato(campionatoId: number | null | undefined): string | null {
  return campionatoId == null ? null : `${COMITATO_PUBBLICO}/campionati/${campionatoId}`;
}

/**
 * La pagina di un girone: classifica, calendario e organico.
 *
 * È quella che si cerca davvero leggendo «Girone B» sotto una partita, ed è
 * il motivo per cui l'id del girone viaggia fin qui.
 */
export function paginaGirone(gironeId: number | null | undefined): string | null {
  return gironeId == null ? null : `${COMITATO_PUBBLICO}/gironi/${gironeId}`;
}
