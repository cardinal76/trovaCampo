import {
  SCONOSCIUTA,
  TUTTE,
  nellaProvincia,
  nomeProvincia,
  opzioniProvincia,
  provinciaValida,
} from './provincia';
import { Societa } from './societa';

function campo(provinciaImpianto: string): Societa {
  return {
    id: provinciaImpianto,
    siglaSocieta: '',
    nomeSocieta: 'Società',
    comitatoRegionale: '',
    nomeImpianto: 'Campo',
    indirizzoImpianto: 'Via Roma 1',
    localitaImpianto: '',
    provinciaImpianto,
  };
}

describe('modelli/provincia', () => {
  it('offre Tutte e le province presenti nei dati, in ordine di nome', () => {
    const opzioni = opzioniProvincia([campo('VT'), campo('RM'), campo('lt'), campo('RM')]);

    expect(opzioni.map((o) => o.etichetta)).toEqual(['Tutte', 'Latina', 'Roma', 'Viterbo']);
    expect(opzioni[1].valore).toBe('LT');
  });

  it('una provincia fuori dal Lazio compare con la sua sigla', () => {
    expect(nomeProvincia('PE')).toBe('PE');
    expect(opzioniProvincia([campo('PE')]).map((o) => o.etichetta)).toEqual(['Tutte', 'PE']);
  });

  it('offre Provincia sconosciuta solo se c è un campo senza', () => {
    expect(opzioniProvincia([campo('RM')]).map((o) => o.valore)).not.toContain(SCONOSCIUTA);
    expect(opzioniProvincia([campo('RM'), campo('')]).map((o) => o.valore)).toEqual([
      TUTTE,
      'RM',
      SCONOSCIUTA,
    ]);
  });

  it('una scelta che non è più fra le opzioni torna a Tutte', () => {
    const opzioni = opzioniProvincia([campo('RM')]);

    expect(provinciaValida('RM', opzioni)).toBe('RM');
    expect(provinciaValida('FR', opzioni)).toBe(TUTTE);
  });

  it('filtra per sigla, per provincia mancante o per niente', () => {
    expect(nellaProvincia(campo('RM'), 'RM')).toBeTrue();
    expect(nellaProvincia(campo('rm'), 'RM')).toBeTrue();
    expect(nellaProvincia(campo('LT'), 'RM')).toBeFalse();
    expect(nellaProvincia(campo(''), SCONOSCIUTA)).toBeTrue();
    expect(nellaProvincia(campo('RM'), SCONOSCIUTA)).toBeFalse();
    expect(nellaProvincia(campo(''), TUTTE)).toBeTrue();
  });
});
