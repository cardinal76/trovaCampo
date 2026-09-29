import { Squadra, aggiornamentoRisultati, dettaglioSquadra, portaleNonRaggiungibile } from './squadra';

describe('dettaglioSquadra', () => {
  const eccellenza: Squadra = {
    campionato: 'ECCELLENZA',
    ente: 'Regionali',
    stagione: '2026/2027',
    girone: 'A',
    squadra: '',
    fuoriClassifica: false,
  };

  it('dice girone ed ente della prima squadra', () => {
    expect(dettaglioSquadra(eccellenza)).toBe('Girone A · Regionali');
  });

  it('distingue la seconda squadra fuori classifica', () => {
    expect(dettaglioSquadra({ ...eccellenza, squadra: 'B', fuoriClassifica: true })).toBe(
      'Girone A · Regionali · squadra B, fuori classifica',
    );
  });

  it('senza girone dice che non sono ancora usciti', () => {
    expect(dettaglioSquadra({ ...eccellenza, girone: undefined, ente: 'Frosinone' })).toBe(
      'Gironi non ancora pubblicati · Frosinone',
    );
  });
});

describe('aggiornamentoRisultati', () => {
  const eccellenza: Squadra = {
    campionato: 'ECCELLENZA',
    stagione: '2026/2027',
    girone: 'A',
    squadra: '',
    fuoriClassifica: false,
  };

  it('dice numero e giorno del comunicato', () => {
    expect(
      aggiornamentoRisultati({ ...eccellenza, risultatiDaComunicato: { numero: 12, data: '2026-10-02' } }),
    ).toBe('Risultati aggiornati al Comunicato Ufficiale n. 12 del 02/10');
  });

  it('tace senza avviso, o senza un comunicato che abbia portato risultati', () => {
    expect(aggiornamentoRisultati(eccellenza)).toBeNull();
    expect(aggiornamentoRisultati({ ...eccellenza, risultatiDaComunicato: null })).toBeNull();
    expect(aggiornamentoRisultati({ ...eccellenza, risultatiDaComunicato: { numero: null } })).toBeNull();
  });

  it('il portale non risponde se lo dice almeno una squadra', () => {
    expect(portaleNonRaggiungibile([eccellenza])).toBeFalse();
    expect(
      portaleNonRaggiungibile([eccellenza, { ...eccellenza, risultatiDaComunicato: { numero: null } }]),
    ).toBeTrue();
  });
});
