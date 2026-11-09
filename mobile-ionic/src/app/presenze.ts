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
const APP_PRESENZE = 'https://presenze.footballer.it';
const COMITATO_PUBBLICO = `${APP_PRESENZE}/comitato/lazio`;

/**
 * Come si entra in Footballer da un club che ne è cliente, per il collegamento
 * «Accedi» della scheda. `stemma` è un percorso relativo a presenze (lo stemma
 * che il club ha caricato) o un indirizzo https; nullo se non ce n'è.
 */
export interface AccessoSocieta {
  alias: string;
  stemma?: string | null;
}

/** Lo stesso formato che la pagina di accesso di presenze accetta. */
const ALIAS_VALIDO = /^[a-z0-9][a-z0-9-]{0,118}$/;
const PERCORSO_STEMMA = /^\/api\/organizzazioni\/\d+\/logo$/;

/**
 * La pagina di accesso di quel club su presenze (nome e stemma sopra il
 * modulo), o `null` se la risposta non ha un alias usabile: da un presenze che
 * non manda il campo, o con un valore strano, il collegamento non si disegna
 * invece di portare in un posto sbagliato.
 */
export function paginaAccesso(accesso: AccessoSocieta | null | undefined): string | null {
  const alias = accesso?.alias;
  return alias && ALIAS_VALIDO.test(alias)
    ? `${APP_PRESENZE}/?societa=${encodeURIComponent(alias)}`
    : null;
}

/**
 * Lo stemma da mostrare nel collegamento, come indirizzo https assoluto: il
 * percorso relativo di presenze si completa con la sua origine, un indirizzo
 * https passa, il resto no (un indirizzo qualunque in un tag immagine è un
 * tracciante).
 */
export function stemmaAccesso(accesso: AccessoSocieta | null | undefined): string | null {
  const stemma = accesso?.stemma;
  if (!stemma) {
    return null;
  }
  if (PERCORSO_STEMMA.test(stemma)) {
    return `${APP_PRESENZE}${stemma}`;
  }
  return stemma.startsWith('https://') ? stemma : null;
}

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
