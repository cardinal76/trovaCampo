import {
  AvvisoPrevisto,
  ProssimaPartita,
  doveSiGioca,
  motivo,
  perGiorno,
  statoAvviso,
} from './prossime';

function partita(dataOra: string, casa = 'LODIGIANI CALCIO 1972'): ProssimaPartita {
  return {
    dataOra,
    casa,
    ospite: 'VIS SEZZE',
    campionato: 'ECCELLENZA',
    giornata: 3,
    campo: null,
    avvisi: [],
  };
}

function avviso(parziale: Partial<AvvisoPrevisto>): AvvisoPrevisto {
  return {
    tipo: 'squadra',
    seguite: ['LODIGIANI CALCIO 1972'],
    distanzaKm: null,
    alle: '2026-09-06T14:00:00+02:00',
    stato: 'previsto',
    ...parziale,
  };
}

describe('prossime partite', () => {
  it('raggruppa per giorno italiano, con Oggi e Domani', () => {
    // Sabato 5 settembre alle 10 di Roma.
    const adesso = new Date('2026-09-05T10:00:00+02:00');
    const giorni = perGiorno(
      [
        partita('2026-09-05T16:30:00+02:00'),
        partita('2026-09-06T11:00:00+02:00'),
        partita('2026-09-06T15:00:00+02:00', 'BOREALE'),
        // Le 23:30 di Roma sono già domenica a Londra, ma il giorno è quello di Roma.
        partita('2026-09-07T23:30:00+02:00'),
      ],
      adesso,
    );

    expect(giorni.map((g) => g.data)).toEqual(['2026-09-05', '2026-09-06', '2026-09-07']);
    expect(giorni.map((g) => g.vicino)).toEqual(['Oggi', 'Domani', null]);
    expect(giorni[1].titolo).toBe('domenica 6 settembre');
    expect(giorni[1].partite.map((p) => p.casa)).toEqual(['LODIGIANI CALCIO 1972', 'BOREALE']);
  });

  it('Domani è giusto anche il giorno del cambio dell’ora', () => {
    // Sabato 28 marzo 2026 alle 23:30: la domenica dura 23 ore.
    const adesso = new Date('2026-03-28T23:30:00+01:00');
    const giorni = perGiorno([partita('2026-03-29T15:00:00+02:00')], adesso);

    expect(giorni[0].vicino).toBe('Domani');
  });

  it('dice il motivo: la squadra seguita o la distanza', () => {
    expect(motivo(avviso({}))).toBe('Segui LODIGIANI CALCIO 1972');
    expect(motivo(avviso({ seguite: ['LODIGIANI CALCIO 1972', 'VIS SEZZE'] }))).toBe(
      'Segui LODIGIANI CALCIO 1972 e VIS SEZZE',
    );
    expect(motivo(avviso({ tipo: 'vicino', seguite: [], distanzaKm: 3.2 }))).toBe('A 3,2 km');
    expect(motivo(avviso({ tipo: 'vicino', seguite: [], distanzaKm: 0 }))).toBe('A 0,0 km');
  });

  it('dice quando arriva l’avviso, o che è arrivato', () => {
    expect(statoAvviso(avviso({}), true)).toBe('Avviso alle 14:00');
    expect(statoAvviso(avviso({ stato: 'inviato' }), true)).toBe('Avviso inviato alle 14:00');
    expect(statoAvviso(avviso({ stato: 'passato' }), true)).toBe('Avviso delle 14:00 non arrivato');
    expect(statoAvviso(avviso({}), false)).toBe('Avviso spento');
    // Arrivato è arrivato, anche se poi lo si è spento.
    expect(statoAvviso(avviso({ stato: 'inviato' }), false)).toBe('Avviso inviato alle 14:00');
  });

  it('scrive il campo con l’indirizzo', () => {
    expect(
      doveSiGioca({
        id: 'x',
        nomeImpianto: 'La Borghesiana "C"',
        indirizzo: 'Via di Vermicino 105',
        localita: 'Roma',
      }),
    ).toBe('La Borghesiana "C" · Via di Vermicino 105, Roma');
    expect(doveSiGioca({ id: 'x', nomeSocieta: 'BOREALE' })).toBe('BOREALE');
    expect(doveSiGioca({ id: 'x' })).toBeNull();
    expect(doveSiGioca(null)).toBeNull();
  });
});
