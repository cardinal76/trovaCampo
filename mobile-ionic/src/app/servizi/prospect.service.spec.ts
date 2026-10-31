import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { AutenticazioneService } from './autenticazione.service';
import { ProspectService } from './prospect.service';

describe('ProspectService', () => {
  let service: ProspectService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/api/admin/prospect`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AutenticazioneService, useValue: { token: () => Promise.resolve('abc') } },
      ],
    });
    service = TestBed.inject(ProspectService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function dopoIlToken(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('chiede la mappa con il token di Keycloak', async () => {
    const risposta = firstValueFrom(service.sullaMappa());
    await dopoIlToken();
    const chiamata = http.expectOne(url);
    expect(chiamata.request.headers.get('Authorization')).toBe('Bearer abc');
    chiamata.flush([]);
    expect(await risposta).toEqual([]);
  });

  it('avvia l\'importazione di una regione', async () => {
    const risposta = firstValueFrom(service.avvia([12], 2));
    await dopoIlToken();
    const chiamata = http.expectOne((r) => r.url === `${url}/importazione`);
    expect(chiamata.request.method).toBe('POST');
    expect(chiamata.request.params.getAll('regioni')).toEqual(['12']);
    expect(chiamata.request.params.get('maxPagine')).toBe('2');
    chiamata.flush({ inCorso: true, ultimo: {}, regioni: [] });
    expect((await risposta).inCorso).toBeTrue();
  });

  it('sceglie i dettagli e riprende l\'ultimo giro', async () => {
    const avvio = firstValueFrom(service.avvia([], undefined, 'FIGC'));
    await dopoIlToken();
    const chiamata = http.expectOne((r) => r.url === `${url}/importazione`);
    expect(chiamata.request.params.get('dettagli')).toBe('figc');
    expect(chiamata.request.params.has('regioni')).toBeFalse();
    chiamata.flush({ inCorso: true, ultimo: {}, regioni: [] });
    await avvio;

    const ripresa = firstValueFrom(service.riprendi());
    await dopoIlToken();
    const riprendi = http.expectOne(`${url}/importazione/riprendi`);
    expect(riprendi.request.method).toBe('POST');
    riprendi.flush({ inCorso: true, ultimo: {}, regioni: [] });
    expect((await ripresa).inCorso).toBeTrue();
  });

  it('senza il ruolo dice cosa manca', async () => {
    const risposta = firstValueFrom(service.sullaMappa()).catch((e: Error) => e);
    await dopoIlToken();
    http.expectOne(url).flush({}, { status: 403, statusText: 'Forbidden' });
    expect(((await risposta) as Error).message).toContain('trovacampo-admin');
  });

  it('collega e chiede la copertura', async () => {
    const collegati = firstValueFrom(service.collega());
    await dopoIlToken();
    const chiamata = http.expectOne(`${url}/collegamento`);
    expect(chiamata.request.method).toBe('POST');
    chiamata.flush({ prospectCalcioLazio: 10, collegati: 6, conCoordinate: 5, copertura: 60, perFonte: {}, perConfidenza: {} });
    expect((await collegati).copertura).toBe(60);

    const copertura = firstValueFrom(service.copertura());
    await dopoIlToken();
    http.expectOne(`${url}/copertura`).flush({ prospectCalcioLazio: 10, collegati: 6, conCoordinate: 5, copertura: 60, perFonte: { OPEN_DATA: 1 }, perConfidenza: {} });
    expect((await copertura).perFonte.OPEN_DATA).toBe(1);
  });

  it('lancia la geocodifica e importa il censimento open data', async () => {
    const avvio = firstValueFrom(service.geocodifica());
    await dopoIlToken();
    const chiamata = http.expectOne(`${url}/geocodifica`);
    expect(chiamata.request.method).toBe('POST');
    chiamata.flush({ avviato: false, messaggio: 'Nessun prospect', stato: { inCorso: false } });
    expect((await avvio).messaggio).toBe('Nessun prospect');

    const openData = firstValueFrom(service.importaOpenData());
    await dopoIlToken();
    const importa = http.expectOne(`${environment.apiUrl}/api/admin/indirizzi/impianti-open-data`);
    expect(importa.request.method).toBe('POST');
    importa.flush({ fonte: 'cmrc-censimento', licenza: 'CC0 1.0', righe: 6636, impianti: 1007, conCalcioA11: 480 });
    expect((await openData).impianti).toBe(1007);
  });
});
