import { SocietaGeolocalizzata } from './societa';

/** Un punto sulla Terra, in gradi decimali. */
export interface Posizione {
  lat: number;
  lng: number;
}

/** Un campo con la sua distanza da chi guarda. */
export interface CampoVicino {
  campo: SocietaGeolocalizzata;
  distanzaKm: number;
}

/** Quanti campi vicini si possono chiedere: pochi, perché la mappa resti leggibile. */
export const SCELTE_NUMERO_VICINI = [1, 3, 5, 10] as const;
export const NUMERO_VICINI_PREDEFINITO = 3;

/** Raggio medio della Terra: per distanze di pochi chilometri l'errore è trascurabile. */
const RAGGIO_TERRA_KM = 6371;

/**
 * Distanza in linea d'aria fra due punti, con la formula dell'haversine.
 *
 * Non è la strada da fare, ma per scegliere i campi più vicini basta: il
 * calcolo resta nel browser, sui campi che la mappa ha già scaricato, senza
 * chiedere niente al server.
 */
export function distanzaKm(da: Posizione, a: Posizione): number {
  const radianti = (gradi: number) => (gradi * Math.PI) / 180;
  const dLat = radianti(a.lat - da.lat);
  const dLng = radianti(a.lng - da.lng);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(radianti(da.lat)) * Math.cos(radianti(a.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * RAGGIO_TERRA_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * Gli n campi più vicini alla posizione, dal più vicino. A parità di
 * distanza (lo stesso impianto per più società) vale l'ordine di arrivo,
 * così il risultato non cambia da una volta all'altra.
 */
export function piuVicini(
  campi: readonly SocietaGeolocalizzata[],
  posizione: Posizione,
  n: number,
): CampoVicino[] {
  if (n <= 0) {
    return [];
  }
  return campi
    .map((campo, indice) => ({ campo, indice, distanzaKm: distanzaKm(posizione, campo) }))
    .sort((a, b) => a.distanzaKm - b.distanzaKm || a.indice - b.indice)
    .slice(0, n)
    .map(({ campo, distanzaKm }) => ({ campo, distanzaKm }));
}

/** "350 m" sotto il chilometro, poi "1,2 km", e senza decimali da 10 km in su. */
export function testoDistanza(km: number): string {
  if (km < 1) {
    return `${Math.max(10, Math.round((km * 1000) / 10) * 10)} m`;
  }
  const decimali = km < 10 ? 1 : 0;
  return `${km.toLocaleString('it-IT', {
    minimumFractionDigits: decimali,
    maximumFractionDigits: decimali,
  })} km`;
}

/** Un numero di vicini fra quelli proposti; qualunque altra cosa torna al predefinito. */
export function numeroViciniValido(valore: unknown): number {
  const numero = Number(valore);
  return (SCELTE_NUMERO_VICINI as readonly number[]).includes(numero)
    ? numero
    : NUMERO_VICINI_PREDEFINITO;
}
