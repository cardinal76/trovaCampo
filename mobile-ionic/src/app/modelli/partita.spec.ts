import {
  Partita,
  campionatoPartita,
  partiteDelCampo,
  quandoPartita,
  squadrePartita,
} from './partita';

describe('partita', () => {
  const boreale: Partita = {
    dataOra: '2026-09-06T11:00:00+02:00',
    casa: 'BOREALE',
    ospite: 'VIGOR PERCONTI',
    campionato: 'ECCELLENZA',
    ente: 'Regionali',
    girone: 'A',
    giornata: 1,
  };

  it('dice giorno e ora con il fuso di Roma, qualunque sia quello del telefono', () => {
    expect(quandoPartita(boreale)).toBe('dom 6 set, 11:00');
    // La stessa ora scritta in UTC.
    expect(quandoPartita({ ...boreale, dataOra: '2026-09-06T09:00:00Z' })).toBe('dom 6 set, 11:00');
  });

  it('mette casa prima e ospite dopo', () => {
    expect(squadrePartita(boreale)).toBe('BOREALE – VIGOR PERCONTI');
  });

  it('dice campionato, girone ed ente, saltando quello che manca', () => {
    expect(campionatoPartita(boreale)).toBe('ECCELLENZA · Girone A · Regionali');
    expect(campionatoPartita({ ...boreale, girone: undefined, ente: undefined })).toBe(
      'ECCELLENZA',
    );
  });

  it('abbina le partite al campo per l id dell impianto, non per nome', () => {
    const partite = { '190': [boreale] };

    expect(partiteDelCampo(partite, { anagraficaImpiantoId: 190 })).toEqual([boreale]);
    expect(partiteDelCampo(partite, { anagraficaImpiantoId: 191 })).toEqual([]);
    expect(partiteDelCampo(partite, {})).toEqual([]);
  });
});
