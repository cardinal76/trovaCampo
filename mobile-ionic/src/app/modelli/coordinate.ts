/**
 * Latitudine e longitudine in un solo campo di testo, come le copia Google
 * Maps (tasto destro sul punto, o "Condividi" sul telefono): "41.507315,
 * 13.058757". Scriverle in due caselle voleva dire incollare e poi tagliare
 * a metà a mano, e sbagliare quale va dove.
 */
export interface Coordinate {
  lat: number;
  lng: number;
}

/**
 * Due numeri separati da virgola, punto e virgola o spazi. Il decimale può
 * essere il punto (Google Maps) o la virgola all'italiana: "41,5073 13,0587"
 * e "41,5073, 13,0587" si leggono uguale, perché il primo numero finisce
 * alla prima cifra seguita da un separatore e poi da un altro numero.
 */
const COPPIA = /^\s*(-?\d+(?:[.,]\d+)?)\s*[,;\s]\s*(-?\d+(?:[.,]\d+)?)\s*$/;

/**
 * Le coordinate scritte nel campo: `undefined` se è vuoto (il server le
 * ricalcola dall'indirizzo), `null` se non si capiscono o sono fuori dal
 * mondo (latitudine oltre ±90, longitudine oltre ±180).
 */
export function leggiCoordinate(testo: string): Coordinate | undefined | null {
  if (testo.trim() === '') {
    return undefined;
  }
  const parti = COPPIA.exec(testo);
  if (!parti) {
    return null;
  }
  const lat = Number(parti[1].replace(',', '.'));
  const lng = Number(parti[2].replace(',', '.'));
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    return null;
  }
  return { lat, lng };
}

/** Nello stesso formato che si incolla: "41.507315, 13.058757". */
export function scriviCoordinate(lat: number | undefined | null, lng: number | undefined | null): string {
  return typeof lat === 'number' && typeof lng === 'number' ? `${lat}, ${lng}` : '';
}
