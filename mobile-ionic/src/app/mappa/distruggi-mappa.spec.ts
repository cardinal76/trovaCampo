import * as L from 'leaflet';
import { distruggiMappa } from './distruggi-mappa';

/**
 * Una mappa distrutta durante un'animazione di zoom riceve {@code zoomend} e
 * {@code moveend} 250 ms dopo: con i listener ancora attaccati, la pagina
 * ridisegnerebbe una mappa che non c'e' piu'. Il difetto non e' riproducibile
 * in modo affidabile (dipende da quanto e' lento chi esegue), quindi qui si
 * controlla il comportamento voluto: dopo la distruzione non scatta piu' niente.
 */
describe('distruggiMappa', () => {
  let contenitore: HTMLElement;
  let errori: string[];
  const ascolta = (evento: ErrorEvent) => errori.push(evento.message);

  const attendi = (ms: number) => new Promise<void>((ok) => setTimeout(ok, ms));

  /** Una mappa come quella della pagina: cerchi sul canvas rimessi a ogni movimento. */
  function mappaCheRidisegna(): L.Map {
    const mappa = L.map(contenitore, { preferCanvas: true }).setView([41.9, 12.5], 10);
    const cerchi = L.layerGroup();
    for (let i = 0; i < 20; i++) {
      L.circleMarker([41.9 + i / 1000, 12.5], { radius: 6 }).addTo(cerchi);
    }
    cerchi.addTo(mappa);
    mappa.on('zoomend moveend', () => cerchi.addTo(mappa));
    return mappa;
  }

  beforeEach(() => {
    contenitore = document.createElement('div');
    contenitore.style.cssText = 'width:300px;height:300px';
    document.body.appendChild(contenitore);
    errori = [];
    window.addEventListener('error', ascolta);
  });

  afterEach(() => {
    window.removeEventListener('error', ascolta);
    contenitore.remove();
  });

  it('distruggiMappa durante lo zoom non lascia errori', async () => {
    const mappa = mappaCheRidisegna();
    await attendi(50);
    mappa.setView([41.9, 12.5], 11, { animate: true });

    distruggiMappa(mappa);
    await attendi(500);

    expect(errori).toEqual([]);
  });

  it('dopo distruggiMappa i listener non scattano piu', () => {
    const mappa = L.map(contenitore).setView([41.9, 12.5], 10);
    const spia = jasmine.createSpy('moveend');
    mappa.on('moveend', spia);

    distruggiMappa(mappa);
    mappa.fire('moveend');

    expect(spia).not.toHaveBeenCalled();
  });
});
