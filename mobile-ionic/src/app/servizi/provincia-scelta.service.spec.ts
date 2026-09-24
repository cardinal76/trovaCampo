import { TestBed } from '@angular/core/testing';
import { TUTTE } from '../modelli/provincia';
import { ProvinciaSceltaService } from './provincia-scelta.service';

describe('ProvinciaSceltaService', () => {
  afterEach(() => localStorage.removeItem('trovacampo.provincia'));

  it('di default nessun filtro', () => {
    expect(TestBed.inject(ProvinciaSceltaService).scelta()).toBe(TUTTE);
  });

  it('ricorda la scelta fra una visita e l altra', () => {
    TestBed.inject(ProvinciaSceltaService).scegli('LT');

    TestBed.resetTestingModule();

    expect(TestBed.inject(ProvinciaSceltaService).scelta()).toBe('LT');
  });

  it('senza localStorage la scelta vale lo stesso finché la pagina resta aperta', () => {
    spyOn(Storage.prototype, 'getItem').and.throwError('bloccato');
    spyOn(Storage.prototype, 'setItem').and.throwError('bloccato');
    const servizio = TestBed.inject(ProvinciaSceltaService);

    expect(servizio.scelta()).toBe(TUTTE);
    expect(() => servizio.scegli('RI')).not.toThrow();
    expect(servizio.scelta()).toBe('RI');
  });
});
