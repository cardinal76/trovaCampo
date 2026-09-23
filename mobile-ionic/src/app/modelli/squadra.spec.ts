import { Squadra, dettaglioSquadra } from './squadra';

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
