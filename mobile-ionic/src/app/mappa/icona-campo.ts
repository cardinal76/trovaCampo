import * as L from 'leaflet';
import { SocietaGeolocalizzata, nomeCompleto, testoSicuro } from '../modelli/societa';

/** Da questo zoom in su i campi si disegnano con l'icona di un campo da calcio. */
export const ZOOM_ICONE = 14;

/** Da questo zoom in su accanto all'icona compare il nome della società. */
export const ZOOM_NOMI = 16;

/** Campo da calcio visto dall'alto, disegnato in SVG per non dipendere da immagini. */
const SVG_CAMPO = `
  <svg viewBox="0 0 28 20" width="35" height="25" aria-hidden="true">
    <rect x="0.5" y="0.5" width="27" height="19" rx="2" fill="#14532d" stroke="#fff" />
    <g fill="none" stroke="#fff" stroke-width="1">
      <rect x="2.5" y="2.5" width="23" height="15" />
      <line x1="14" y1="2.5" x2="14" y2="17.5" />
      <circle cx="14" cy="10" r="3" />
      <rect x="2.5" y="6" width="3.5" height="8" />
      <rect x="22" y="6" width="3.5" height="8" />
    </g>
  </svg>`;

/** Icona del campo con il nome, che il CSS mostra solo agli zoom più alti. */
export function iconaCampo(campo: SocietaGeolocalizzata): L.DivIcon {
  return L.divIcon({
    className: 'icona-campo',
    html: `${SVG_CAMPO}<span class="nome-campo">${testoSicuro(nomeCompleto(campo))}</span>`,
    iconSize: [35, 25],
    iconAnchor: [17, 12],
    popupAnchor: [0, -12],
    tooltipAnchor: [18, 0],
  });
}

/** Accende i nomi dei campi (vedi styles.scss) quando lo zoom è abbastanza alto. */
export function aggiornaNomiCampi(mappa: L.Map): void {
  mappa.getContainer().classList.toggle('mostra-nomi-campi', mappa.getZoom() >= ZOOM_NOMI);
}
