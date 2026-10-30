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

  it('senza il ruolo dice cosa manca', async () => {
    const risposta = firstValueFrom(service.sullaMappa()).catch((e: Error) => e);
    await dopoIlToken();
    http.expectOne(url).flush({}, { status: 403, statusText: 'Forbidden' });
    expect(((await risposta) as Error).message).toContain('trovacampo-admin');
  });
});
