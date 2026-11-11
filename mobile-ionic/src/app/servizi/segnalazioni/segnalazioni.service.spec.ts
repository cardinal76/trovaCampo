import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AutenticazioneService } from '../amministrazione/autenticazione.service';
import { SegnalazioniService } from './segnalazioni.service';

describe('SegnalazioniService', () => {
  let service: SegnalazioniService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/api/segnalazioni`;
  const urlAdmin = `${environment.apiUrl}/api/admin/segnalazioni`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AutenticazioneService, useValue: { token: () => Promise.resolve('abc') } },
      ],
    });
    service = TestBed.inject(SegnalazioniService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function dopoIlToken(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it("l'invio e' pubblico: nessun token", () => {
    service.invia({ tipo: 'ALTRO', corretto: 'x' }).subscribe();
    const chiamata = http.expectOne(url);
    expect(chiamata.request.headers.has('Authorization')).toBeFalse();
    chiamata.flush(null);
  });

  it("l'elenco va chiesto con il token", async () => {
    const risposta = firstValueFrom(service.elenco());
    await dopoIlToken();
    const chiamata = http.expectOne(urlAdmin);
    expect(chiamata.request.headers.get('Authorization')).toBe('Bearer abc');
    chiamata.flush([]);
    expect(await risposta).toEqual([]);
  });

  it('segna una segnalazione come gestita', async () => {
    const risposta = firstValueFrom(service.segnaGestita('a/b', true));
    await dopoIlToken();
    const chiamata = http.expectOne(`${urlAdmin}/a%2Fb`);
    expect(chiamata.request.method).toBe('PUT');
    expect(chiamata.request.body).toEqual({ gestita: true });
    chiamata.flush({ id: 'a/b', gestita: true });
    expect((await risposta).gestita).toBeTrue();
  });
});
