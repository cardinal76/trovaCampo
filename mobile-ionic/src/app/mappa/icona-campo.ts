import * as L from 'leaflet';
import { SocietaGeolocalizzata, nomeCompleto, testoSicuro } from '../modelli/societa';

/**
 * Da questo zoom in su l'icona del campo è un elemento HTML, che può
 * mostrare il nome della società; sotto, nella pagina Mappa, la stessa
 * icona è disegnata sul canvas (campoSuCanvas).
 */
export const ZOOM_ICONE = 14;

/** Da questo zoom in su accanto all'icona compare il nome della società. */
export const ZOOM_NOMI = 16;

/** Campo da calcio visto dall'alto, disegnato in SVG per non dipendere da immagini. */
const SVG_CAMPO = `
  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 28 20" width="35" height="25" aria-hidden="true">
    <rect x="0.5" y="0.5" width="27" height="19" rx="2" fill="#14532d" stroke="#fff" />
    <g fill="none" stroke="#fff" stroke-width="1">
      <rect x="2.5" y="2.5" width="23" height="15" />
      <line x1="14" y1="2.5" x2="14" y2="17.5" />
      <circle cx="14" cy="10" r="3" />
      <rect x="2.5" y="6" width="3.5" height="8" />
      <rect x="22" y="6" width="3.5" height="8" />
    </g>
  </svg>`;

/** Dimensioni dell'icona in pixel, uguali per l'HTML e per il canvas. */
const LARGHEZZA = 35;
const ALTEZZA = 25;

/** Icona del campo con il nome, che il CSS mostra solo agli zoom più alti. */
export function iconaCampo(campo: SocietaGeolocalizzata): L.DivIcon {
  return L.divIcon({
    className: 'icona-campo',
    html: `${SVG_CAMPO}<span class="nome-campo">${testoSicuro(nomeCompleto(campo))}</span>`,
    iconSize: [LARGHEZZA, ALTEZZA],
    iconAnchor: [17, 12],
    popupAnchor: [0, -12],
    tooltipAnchor: [18, 0],
  });
}

/** Accende i nomi dei campi (vedi styles.scss) quando lo zoom è abbastanza alto. */
export function aggiornaNomiCampi(mappa: L.Map): void {
  mappa.getContainer().classList.toggle('mostra-nomi-campi', mappa.getZoom() >= ZOOM_NOMI);
}

let immagine: Promise<HTMLImageElement> | null = null;

/** L'SVG del campo come immagine per il canvas, caricata una volta sola. */
export function immagineCampo(): Promise<HTMLImageElement> {
  if (!immagine) {
    const img = new Image();
    img.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(SVG_CAMPO.trim())}`;
    immagine = img.decode().then(() => img);
  }
  return immagine;
}

interface OpzioniCampoSuCanvas extends L.CircleMarkerOptions {
  immagine: HTMLImageElement;
}

/**
 * Un CircleMarker che al posto del cerchio disegna l'icona del campo, con il
 * renderer canvas della mappa: migliaia di campi restano un solo elemento
 * del DOM. Del cerchio restano l'area sensibile (il raggio) e quindi
 * tooltip e popup. Sostituisce il metodo interno _updatePath di Leaflet 1.9:
 * va ricontrollato se si aggiorna Leaflet.
 */
const CampoSuCanvas = (L.CircleMarker as unknown as typeof L.Class).extend({
  _updatePath(this: {
    options: OpzioniCampoSuCanvas;
    _renderer: { _ctx?: CanvasRenderingContext2D };
    _point: L.Point;
  }) {
    this._renderer._ctx?.drawImage(
      this.options.immagine,
      this._point.x - LARGHEZZA / 2,
      this._point.y - ALTEZZA / 2,
      LARGHEZZA,
      ALTEZZA,
    );
  },
}) as unknown as new (posizione: L.LatLngExpression, opzioni: OpzioniCampoSuCanvas) => L.CircleMarker;

export function campoSuCanvas(
  posizione: L.LatLngExpression,
  immagine: HTMLImageElement,
): L.CircleMarker {
  return new CampoSuCanvas(posizione, { immagine, radius: LARGHEZZA / 2 });
}
