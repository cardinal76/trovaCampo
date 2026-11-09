import { TestBed } from '@angular/core/testing';
import { SquadraSeguita, SquadreSeguiteService } from './squadre-seguite.service';

const CHIAVE = 'trovacampo.squadreSeguite';

function squadra(societa: number): SquadraSeguita {
  return {
    chiave: `${societa}|eccellenza|regionali|`,
    societa: `SOCIETA ${societa}`,
    societaId: `id-${societa}`,
    campionato: 'ECCELLENZA',
    dettaglio: 'Girone A',
  };
}

describe('SquadreSeguiteService', () => {
  afterEach(() => localStorage.removeItem(CHIAVE));

  function servizio(): SquadreSeguiteService {
    return TestBed.inject(SquadreSeguiteService);
  }

  it('ricorda le squadre seguite fra una visita e l altra', () => {
    servizio().aggiungi(squadra(1));

    TestBed.resetTestingModule();
    expect(servizio().chiavi()).toEqual(['1|eccellenza|regionali|']);
    expect(servizio().segue('1|eccellenza|regionali|')).toBeTrue();
  });

  it('la stessa squadra una volta sola, e si smette di seguirla', () => {
    const seguite = servizio();
    seguite.aggiungi(squadra(1));
    seguite.aggiungi(squadra(1));
    seguite.aggiungi(squadra(2));
    seguite.togli('1|eccellenza|regionali|');

    expect(seguite.chiavi()).toEqual(['2|eccellenza|regionali|']);
  });

  it('non più di trenta, come accetta il server', () => {
    const seguite = servizio();
    for (let i = 1; i <= 30; i++) {
      expect(seguite.aggiungi(squadra(i))).toBeTrue();
    }

    expect(seguite.aggiungi(squadra(31))).toBeFalse();
    expect(seguite.seguite().length).toBe(30);
  });

  it('scarta un archivio rovinato', () => {
    localStorage.setItem(CHIAVE, JSON.stringify([{ chiave: 3 }, squadra(4), 'x']));
    expect(servizio().chiavi()).toEqual(['4|eccellenza|regionali|']);

    TestBed.resetTestingModule();
    localStorage.setItem(CHIAVE, '{non json');
    expect(servizio().chiavi()).toEqual([]);
  });

  it('con l archivio bloccato valgono finché la pagina è aperta', () => {
    spyOn(localStorage, 'setItem').and.throwError('bloccato');
    servizio().aggiungi(squadra(5));

    expect(servizio().segue('5|eccellenza|regionali|')).toBeTrue();
  });
});
