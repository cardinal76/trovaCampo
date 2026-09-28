import * as L from 'leaflet';
import { SocietaGeolocalizzata, nomeCompleto, testoSicuro } from '../modelli/societa';

/**
 * Da questo zoom in su l'icona del campo è un elemento HTML, che può
 * mostrare il nome della società; sotto, nella pagina Mappa, la stessa
 * icona è disegnata sul canvas (campoSuCanvas).
 */
export const ZOOM_ICONE = 14;

/** Da questo zoom in su accanto all'icona compare il nome della società. */
export const ZOOM_NOMI = 18;

/**
 * Da questo zoom in su, nella pagina Mappa, il campo di una società con lo
 * stemma si mostra con lo stemma al posto dell'icona del campo. A 14 un
 * segnaposto di 36 pixel copre circa 350 metri: due campi così vicini sono
 * rari, e gli stemmi si leggono senza coprirsi. Non può stare sotto
 * ZOOM_ICONE: sul canvas si disegna solo l'icona del campo, e uno stemma
 * sul canvas lo "sporcherebbe" (immagine di un altro sito) oltre a doverli
 * scaricare tutti all'apertura. Stando sopra, gli stemmi si scaricano solo
 * per i campi dentro la vista, e solo quando ci si avvicina.
 */
export const ZOOM_STEMMI = 14;

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

/** Dimensioni dell'icona HTML in pixel. */
const LARGHEZZA = 35;
const ALTEZZA = 25;

/** Da lontano, sul canvas, l'icona è più piccola: i campi vicini si coprono meno. */
const LARGHEZZA_CANVAS = 21;
const ALTEZZA_CANVAS = 15;

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

/**
 * Come si mostra un campo sulla mappa a questo zoom: sul canvas da lontano,
 * poi con lo stemma della società se ce l'ha (e se non ha già dato errore),
 * altrimenti con l'icona del campo. Senza stemma resta l'icona del campo e
 * non il cerchio con l'iniziale dell'elenco: sulla mappa dice subito "qui
 * c'è un campo", mentre un cerchio con una lettera sembrerebbe uno stemma
 * che non si è caricato.
 */
export type TipoSegnaposto = 'canvas' | 'campo' | 'stemma';

export function tipoSegnaposto(
  campo: SocietaGeolocalizzata,
  zoom: number,
  stemmiFalliti: ReadonlySet<string> = new Set(),
): TipoSegnaposto {
  if (zoom < ZOOM_ICONE) {
    return 'canvas';
  }
  const stemma = stemmaDi(campo);
  return zoom >= ZOOM_STEMMI && stemma && !stemmiFalliti.has(stemma) ? 'stemma' : 'campo';
}

/** L'indirizzo dello stemma, se c'è ed è https (come in stemma-societa). */
export function stemmaDi(campo: SocietaGeolocalizzata): string | null {
  const indirizzo = campo.logoUrl?.trim();
  return indirizzo && indirizzo.startsWith('https://') ? indirizzo : null;
}

/** Diametro del tondo dello stemma e altezza della punta sotto, in pixel. */
const DIAMETRO_STEMMA = 36;
const PUNTA_STEMMA = 8;

/**
 * Lo stemma come segnaposto: un tondo bianco con ombra e una punta sotto,
 * che tocca il punto esatto del campo (l'ancora di Leaflet). L'area che si
 * tocca è tutto il tondo, più grande dell'icona del campo.
 *
 * È un <img> nell'HTML del segnaposto e non un disegno sul canvas: così
 * l'immagine del portale LND non passa per un canvas (che si "sporcherebbe"
 * con un'immagine di un altro sito) e si scarica solo quando il segnaposto
 * entra nella mappa. `data-stemma` porta l'id del campo: se l'immagine dà
 * errore, la pagina Mappa rimette l'icona del campo (vedi mappa.page.ts).
 *
 * `vicino` è per "Vicino a me": l'anello arancione dei campi vicini sta
 * attorno al punto del campo, cioè alla punta, e con lo stemma sopra
 * sembrerebbe un altro segnaposto. Per questi l'anello lo porta il tondo.
 */
export function iconaStemma(campo: SocietaGeolocalizzata, vicino = false): L.DivIcon {
  const altezza = DIAMETRO_STEMMA + PUNTA_STEMMA;
  return L.divIcon({
    className: `icona-campo icona-stemma${vicino ? ' stemma-vicino' : ''}`,
    html:
      `<span class="tondo-stemma"><img src="${testoSicuro(stemmaDi(campo) ?? '')}" alt=""` +
      ` data-stemma="${testoSicuro(campo.id)}" referrerpolicy="no-referrer" decoding="async"` +
      ` draggable="false" width="${DIAMETRO_STEMMA - 8}" height="${DIAMETRO_STEMMA - 8}" /></span>` +
      `<span class="nome-campo">${testoSicuro(nomeCompleto(campo))}</span>`,
    iconSize: [DIAMETRO_STEMMA, altezza],
    iconAnchor: [DIAMETRO_STEMMA / 2, altezza],
    popupAnchor: [0, -altezza],
    tooltipAnchor: [DIAMETRO_STEMMA / 2, -altezza / 2 - PUNTA_STEMMA / 2],
  });
}

/** L'icona che corrisponde al tipo scelto da {@link tipoSegnaposto}. */
export function iconaPerTipo(
  campo: SocietaGeolocalizzata,
  tipo: TipoSegnaposto,
  vicino = false,
): L.DivIcon {
  return tipo === 'stemma' ? iconaStemma(campo, vicino) : iconaCampo(campo);
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
      this._point.x - LARGHEZZA_CANVAS / 2,
      this._point.y - ALTEZZA_CANVAS / 2,
      LARGHEZZA_CANVAS,
      ALTEZZA_CANVAS,
    );
  },
}) as unknown as new (posizione: L.LatLngExpression, opzioni: OpzioniCampoSuCanvas) => L.CircleMarker;

export function campoSuCanvas(
  posizione: L.LatLngExpression,
  immagine: HTMLImageElement,
): L.CircleMarker {
  return new CampoSuCanvas(posizione, { immagine, radius: LARGHEZZA_CANVAS / 2 });
}
