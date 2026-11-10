/**
 * Solo per i test: scova da dove nasce
 * «Cannot read properties of undefined (reading 'clearRect')».
 *
 * In CI, a caso, un test di MappaPage fallisce con quell'errore, che Leaflet
 * lancia in modo asincrono quando il canvas di una mappa ha gia' perso il
 * contesto. Jasmine lo riporta come «Uncaught ... thrown», senza stack, e al
 * test che sta girando in quel momento. Qui si ricorda chi ha distrutto il
 * canvas e chi ha chiesto l'ultimo ridisegno, e se `_clear` trova il contesto
 * mancante lo si stampa. Il comportamento non cambia: l'errore resta, e il test
 * fallisce come prima, ma ora il log dice da dove viene.
 *
 * Sta in un *.spec.ts perche' finisca nello stesso bundle dei test (e quindi nella
 * stessa copia di Leaflet): come polyfill avrebbe una copia sua e non servirebbe.
 *
 * Da togliere quando la causa e' trovata e corretta.
 */
import * as L from 'leaflet';

interface CanvasDiagnosticato {
  _ctx?: unknown;
  _map?: unknown;
  _dbgDistrutto?: string;
  _dbgRichiesta?: string;
  _requestRedraw: (...argomenti: unknown[]) => unknown;
  _destroyContainer: (...argomenti: unknown[]) => unknown;
  _clear: (...argomenti: unknown[]) => unknown;
}

const canvas = L.Canvas.prototype as unknown as CanvasDiagnosticato;

const richiesta = canvas._requestRedraw;
canvas._requestRedraw = function (this: CanvasDiagnosticato, ...argomenti: unknown[]) {
  this._dbgRichiesta = `${new Error('richiesta di ridisegno').stack}\n(con la mappa: ${!!this._map})`;
  return richiesta.apply(this, argomenti);
};

const distrutto = canvas._destroyContainer;
canvas._destroyContainer = function (this: CanvasDiagnosticato, ...argomenti: unknown[]) {
  this._dbgDistrutto = new Error('contenitore distrutto').stack;
  return distrutto.apply(this, argomenti);
};

const pulisci = canvas._clear;
canvas._clear = function (this: CanvasDiagnosticato, ...argomenti: unknown[]) {
  if (!this._ctx) {
    console.error(
      [
        '[diagnostica] Canvas._clear senza contesto',
        '--- contenitore distrutto da:',
        this._dbgDistrutto ?? "(mai distrutto: il contesto non e' mai esistito)",
        '--- ultima richiesta di ridisegno:',
        this._dbgRichiesta ?? '(nessuna: ridisegno partito da _updatePaths)',
        '--- chiamato da:',
        new Error('_clear').stack,
      ].join('\n'),
    );
  }
  return pulisci.apply(this, argomenti);
};
