import { ArchivioLocale, archivioLocale } from './archivio-locale';

describe('ArchivioLocale', () => {
  const CHIAVE = 'trovacampo.prova.archivio';

  // L'originale, preso subito: nel test dell'archivio bloccato lo spy è ancora
  // attivo quando gira l'afterEach, e pulire con localStorage.removeItem lancerebbe.
  const rimuovi = Storage.prototype.removeItem;

  afterEach(() => rimuovi.call(localStorage, CHIAVE));

  it('scrive e rilegge un testo', () => {
    expect(archivioLocale.scrivi(CHIAVE, 'ciao')).toBeTrue();
    expect(archivioLocale.leggi(CHIAVE)).toBe('ciao');
    expect(localStorage.getItem(CHIAVE)).toBe('ciao');
  });

  it("una chiave che non c'è è null, e togli la fa sparire", () => {
    expect(archivioLocale.leggi(CHIAVE)).toBeNull();
    archivioLocale.scrivi(CHIAVE, 'x');
    archivioLocale.togli(CHIAVE);
    expect(archivioLocale.leggi(CHIAVE)).toBeNull();
  });

  it('salva e rilegge un JSON', () => {
    expect(archivioLocale.scriviJson(CHIAVE, { a: 1, b: ['x'] })).toBeTrue();
    expect(archivioLocale.leggiJson(CHIAVE)).toEqual({ a: 1, b: ['x'] });
  });

  it('un JSON rotto o assente si legge come null', () => {
    expect(archivioLocale.leggiJson(CHIAVE)).toBeNull();
    localStorage.setItem(CHIAVE, '{non json');
    expect(archivioLocale.leggiJson(CHIAVE)).toBeNull();
  });

  it("con l'archivio bloccato non lancia: legge null e scrivere dice falso", () => {
    spyOn(Storage.prototype, 'getItem').and.throwError('bloccato');
    spyOn(Storage.prototype, 'setItem').and.throwError('bloccato');
    spyOn(Storage.prototype, 'removeItem').and.throwError('bloccato');

    expect(archivioLocale.leggi(CHIAVE)).toBeNull();
    expect(archivioLocale.leggiJson(CHIAVE)).toBeNull();
    expect(archivioLocale.scrivi(CHIAVE, 'x')).toBeFalse();
    expect(archivioLocale.scriviJson(CHIAVE, 1)).toBeFalse();
    expect(() => archivioLocale.togli(CHIAVE)).not.toThrow();
  });

  it("anche l'accesso a localStorage che lancia (cookie bloccati) non rompe", () => {
    const archivio = new ArchivioLocale(() => {
      throw new DOMException('bloccato', 'SecurityError');
    });

    expect(archivio.leggi(CHIAVE)).toBeNull();
    expect(archivio.scrivi(CHIAVE, 'x')).toBeFalse();
    expect(() => archivio.togli(CHIAVE)).not.toThrow();
  });
});
