import type { CasoPosizione } from '../servizi/diagnosi.service';
import { Posizione } from './vicini';

/**
 * Le indicazioni stradali fino a un campo si chiedono al navigatore che si ha
 * già (Google Maps, in app o nel browser), con un semplice link: niente
 * chiavi, niente servizi di percorso da mantenere, e sul telefono il link
 * apre direttamente l'app di navigazione.
 */
const GOOGLE_MAPS_PERCORSO = 'https://www.google.com/maps/dir/?api=1';

/** Sei decimali sono una decina di centimetri: di più allunga solo il link. */
function coordinate(punto: Posizione): string {
  return `${Number(punto.lat.toFixed(6))},${Number(punto.lng.toFixed(6))}`;
}

/**
 * Il link di Google Maps con il percorso fino al campo.
 *
 * La partenza è l'indirizzo scritto, se c'è: chi lo scrive vuole partire da
 * lì anche se la sua posizione è nota. Altrimenti la posizione di chi guarda,
 * se la si conosce. Senza né l'uno né l'altra `origin` resta fuori, e Google
 * parte da solo dalla posizione del dispositivo: è quello che vuol dire il
 * campo vuoto, "La mia posizione".
 */
export function linkPercorso(
  destinazione: Posizione,
  indirizzo: string | null | undefined,
  posizione: Posizione | null | undefined,
): string {
  const scritto = indirizzo?.trim() ?? '';
  const origine = scritto
    ? encodeURIComponent(scritto)
    : posizione
      ? coordinate(posizione)
      : null;
  const partenza = origine === null ? '' : `&origin=${origine}`;
  return `${GOOGLE_MAPS_PERCORSO}${partenza}&destination=${coordinate(destinazione)}`;
}

/**
 * Vero se da qui il browser può dare la posizione, magari dopo un permesso:
 * allora il campo "Partenza" vuoto vuol dire "La mia posizione". Non la dà
 * mai una pagina non in HTTPS, un browser senza geolocalizzazione o un sito
 * che la vieta; bloccata dall'utente invece sì, una volta sbloccata, e il
 * riquadro della diagnosi spiega come.
 */
export function partenzaDallaPosizione(caso: CasoPosizione): boolean {
  return caso !== 'non-sicura' && caso !== 'non-supportata' && caso !== 'vietata-dal-sito';
}

/** Il segnaposto del campo "Partenza", finché è vuoto. */
export function suggerimentoPartenza(caso: CasoPosizione): string {
  return partenzaDallaPosizione(caso) ? 'La mia posizione' : 'Scrivi da dove parti';
}
