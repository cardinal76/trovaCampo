import * as L from 'leaflet';

/**
 * Distrugge una mappa Leaflet senza lasciare niente che scatti dopo.
 *
 * {@code map.remove()} non toglie i listener di chi si e' registrato, e un
 * movimento ancora in corso (uno zoom animato dura 250 ms) fa partire
 * {@code zoomend} e {@code moveend} dopo la distruzione. I listener della
 * pagina allora rimettono i cerchi sul canvas di una mappa che non c'e' piu':
 * un frame dopo Leaflet lancia «Cannot read properties of undefined (reading
 * 'clearRect')». Compare a caso, a seconda di quanto e' lento chi esegue, e nei
 * test lo si attribuisce al test che sta girando in quel momento.
 *
 * Qui si fermano prima i listener (compresi quelli di Leaflet: la mappa e'
 * finita) e poi la si distrugge.
 */
export function distruggiMappa(mappa: L.Map): void {
  mappa.off();
  mappa.remove();
}
