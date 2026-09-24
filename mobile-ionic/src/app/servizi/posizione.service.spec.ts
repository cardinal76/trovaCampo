import { TestBed } from '@angular/core/testing';
import { ErrorePosizione, PosizioneService } from './posizione.service';

describe('PosizioneService', () => {
  let servizio: PosizioneService;

  beforeEach(() => {
    servizio = TestBed.inject(PosizioneService);
  });

  /** Fa rispondere il GPS simulato con un errore del codice dato (1, 2 o 3). */
  function rispondeConErrore(codice: number): void {
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((_, errore) =>
      errore!({ code: codice } as GeolocationPositionError),
    );
  }

  async function motivo(): Promise<string> {
    try {
      await servizio.attuale();
      return 'nessun errore';
    } catch (errore) {
      expect(errore).toEqual(jasmine.any(ErrorePosizione));
      return (errore as ErrorePosizione).motivo;
    }
  }

  it('dà latitudine e longitudine del dispositivo', async () => {
    spyOn(navigator.geolocation, 'getCurrentPosition').and.callFake((riuscita) =>
      riuscita({ coords: { latitude: 41.9, longitude: 12.5 } } as GeolocationPosition),
    );

    expect(await servizio.attuale()).toEqual({ lat: 41.9, lng: 12.5 });
  });

  it('permesso negato', async () => {
    rispondeConErrore(1);
    expect(await motivo()).toBe('negata');
  });

  it('posizione non disponibile', async () => {
    rispondeConErrore(2);
    expect(await motivo()).toBe('non-disponibile');
  });

  it('tempo scaduto', async () => {
    rispondeConErrore(3);
    expect(await motivo()).toBe('scaduta');
  });

  it('browser senza geolocalizzazione', async () => {
    spyOnProperty(navigator, 'geolocation').and.returnValue(undefined as never);
    expect(await motivo()).toBe('non-supportata');
  });

  it('pagina non in HTTPS: lo dice senza nemmeno chiedere la posizione', async () => {
    spyOnProperty(window, 'isSecureContext').and.returnValue(false);
    const richiesta = spyOn(navigator.geolocation, 'getCurrentPosition');

    expect(await motivo()).toBe('non-sicura');
    expect(richiesta).not.toHaveBeenCalled();
  });
});
