import {
  Partita,
  campionatoPartita,
  dataPartita,
  partiteDelCampo,
  daSeguire,
  fondiLive,
  inFinestra,
  punteggioLive,
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

  it('dice il punteggio in diretta', () => {
    const inDiretta: Partita = { ...boreale, live: { golCasa: 0, golOspite: 3 } };

    expect(punteggioLive(inDiretta)).toBe('0 – 3');
    expect(punteggioLive(boreale)).toBeNull();
  });

  it('segue solo le partite che si possono star giocando: dal quarto d ora prima a 2h15 dopo', () => {
    const conId: Partita = { ...boreale, id: 7 };
    const orario = Date.parse(boreale.dataOra);

    expect(inFinestra(conId, orario - 16 * 60_000)).toBeFalse();
    expect(inFinestra(conId, orario - 14 * 60_000)).toBeTrue();
    expect(inFinestra(conId, orario + 134 * 60_000)).toBeTrue();
    expect(inFinestra(conId, orario + 136 * 60_000)).toBeFalse();
    // Senza l'id di presenze non si può chiedere il punteggio.
    expect(inFinestra(boreale, orario)).toBeFalse();
    expect(daSeguire([conId, { ...boreale, id: 8, dataOra: '2026-09-20T11:00:00+02:00' }], orario)).toEqual([7]);
  });

  it('fonde i punteggi solo nelle partite chieste, e toglie il live a chi non c e piu', () => {
    const prima: Partita[] = [
      { ...boreale, id: 1, live: { golCasa: 0, golOspite: 0 } },
      { ...boreale, id: 2, live: { golCasa: 1, golOspite: 1 } },
      { ...boreale, id: 3 },
    ];

    const dopo = fondiLive(prima, [1, 2], { '1': { golCasa: 1, golOspite: 0 } });

    expect(dopo[0].live).toEqual({ golCasa: 1, golOspite: 0 });
    expect(dopo[1].live).toBeNull();
    expect(dopo[2]).toBe(prima[2]);
  });

  it('dice giorno e ora con il fuso di Roma, qualunque sia quello del telefono', () => {
    expect(quandoPartita(boreale)).toBe('dom 6 set, 11:00');
    // La stessa ora scritta in UTC.
    expect(quandoPartita({ ...boreale, dataOra: '2026-09-06T09:00:00Z' })).toBe('dom 6 set, 11:00');
  });

  it('smonta la data per il riquadro del calendario, con l ora di Roma', () => {
    const lontano = new Date('2026-08-01T10:00:00Z');

    expect(dataPartita({ ...boreale, dataOra: '2026-09-06T09:00:00Z' }, lontano)).toEqual({
      giorno: 'dom',
      numero: '6',
      mese: 'set',
      ora: '11:00',
      vicino: null,
    });
  });

  it('chiama per nome oggi e domani, contando i giorni a Roma', () => {
    // Le 23:30 del 5 settembre a Roma sono ancora il 5, anche se in UTC.
    const sabatoSera = new Date('2026-09-05T21:30:00Z');

    expect(dataPartita(boreale, sabatoSera).vicino).toBe('Domani');
    expect(dataPartita(boreale, new Date('2026-09-06T06:00:00Z')).vicino).toBe('Oggi');
    expect(dataPartita(boreale, new Date('2026-09-04T10:00:00Z')).vicino).toBeNull();
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
