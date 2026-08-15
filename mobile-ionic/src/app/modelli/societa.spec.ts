import { Societa, haCoordinate, indirizzoCompleto, nomeCompleto } from './societa';

function societa(valori: Partial<Societa> = {}): Societa {
  return {
    id: '1',
    siglaSocieta: 'A.S.D.',
    nomeSocieta: 'Certosa Calcio',
    comitatoRegionale: 'LAZIO',
    nomeImpianto: 'Campo Certosa',
    indirizzoImpianto: 'Via della Certosa 12',
    localitaImpianto: 'Roma',
    provinciaImpianto: 'RM',
    ...valori,
  };
}

describe('modelli/societa', () => {
  it('unisce sigla e nome della società', () => {
    expect(nomeCompleto(societa())).toBe('A.S.D. Certosa Calcio');
  });

  it('non lascia spazi in testa quando la sigla manca', () => {
    expect(nomeCompleto(societa({ siglaSocieta: '' }))).toBe('Certosa Calcio');
  });

  it('compone l indirizzo con località e provincia', () => {
    expect(indirizzoCompleto(societa())).toBe('Via della Certosa 12, Roma (RM)');
  });

  it('non produce virgole e parentesi vuote per i campi inseriti a mano', () => {
    const inseritoAMano = societa({ localitaImpianto: '', provinciaImpianto: '' });

    expect(indirizzoCompleto(inseritoAMano)).toBe('Via della Certosa 12');
  });

  it('riconosce le società di cui si conosce la posizione', () => {
    expect(haCoordinate(societa({ lat: 41.8919, lng: 12.4863 }))).toBeTrue();
    expect(haCoordinate(societa())).toBeFalse();
    expect(haCoordinate(societa({ lat: 41.8919 }))).toBeFalse();
  });
});
