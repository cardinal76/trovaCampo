import { TestBed } from '@angular/core/testing';
import { NumeroViciniService } from './numero-vicini.service';

const CHIAVE = 'trovacampo.numeroVicini';

describe('NumeroViciniService', () => {
  afterEach(() => localStorage.removeItem(CHIAVE));

  function servizio(): NumeroViciniService {
    return TestBed.inject(NumeroViciniService);
  }

  it('senza una scelta ricordata ne mostra 3', () => {
    expect(servizio().numero()).toBe(3);
  });

  it('ricorda la scelta fra una visita e l altra', () => {
    servizio().scegli(5);

    expect(servizio().numero()).toBe(5);
    expect(localStorage.getItem(CHIAVE)).toBe('5');
    TestBed.resetTestingModule();
    expect(TestBed.inject(NumeroViciniService).numero()).toBe(5);
  });

  it('un valore ricordato non valido torna al predefinito', () => {
    localStorage.setItem(CHIAVE, '999');
    expect(servizio().numero()).toBe(3);
  });

  it('con l archivio bloccato la scelta vale lo stesso finché la pagina è aperta', () => {
    spyOn(localStorage, 'setItem').and.throwError('bloccato');
    servizio().scegli(10);
    expect(servizio().numero()).toBe(10);
  });
});
