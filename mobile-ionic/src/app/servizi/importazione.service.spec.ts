import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { AutenticazioneService } from './autenticazione.service';
import { ImportazioneService } from './importazione.service';

describe('ImportazioneService', () => {
  let service: ImportazioneService;
  let http: HttpTestingController;
  let token: jasmine.Spy<() => Promise<string>>;
  const url = `${environment.apiUrl}/api/admin/importazione`;
  const file = new File(['contenuto'], 'campi.xlsx');

  beforeEach(() => {
    token = jasmine.createSpy('token').and.resolveTo('abc.def.ghi');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // keycloak-js non si avvia nei test: il token lo dà questo finto servizio.
        { provide: AutenticazioneService, useValue: { token } },
      ],
    });

    service = TestBed.inject(ImportazioneService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** La richiesta parte dopo che il token è pronto, cioè dopo una promessa. */
  async function richiesta(prova: boolean) {
    const esito = firstValueFrom(service.importa(file, prova)).catch((e: Error) => e);
    await Promise.resolve();
    await Promise.resolve();
    return { esito, http: http.expectOne((r) => r.url === url) };
  }

  it('invia il file con il token di Keycloak e la modalità prova', async () => {
    const { esito, http: chiamata } = await richiesta(true);

    expect(chiamata.request.method).toBe('POST');
    expect(chiamata.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    expect(chiamata.request.params.get('prova')).toBe('true');
    expect((chiamata.request.body as FormData).get('file')).toEqual(jasmine.any(File));
    chiamata.flush({ prova: true });

    expect(await esito).toEqual(jasmine.objectContaining({ prova: true }));
  });

  it('dice quando manca il ruolo', async () => {
    const { esito, http: chiamata } = await richiesta(false);
    chiamata.flush({}, { status: 403, statusText: 'Forbidden' });

    expect(((await esito) as Error).message).toBe('Il tuo utente non ha il ruolo trovacampo-admin.');
  });

  it('dice quando la sessione è scaduta', async () => {
    const { esito, http: chiamata } = await richiesta(false);
    chiamata.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(((await esito) as Error).message).toContain('sessione è scaduta');
  });

  it('mostra il motivo dato dal server per un file non valido', async () => {
    const { esito, http: chiamata } = await richiesta(true);
    chiamata.flush(
      { errore: 'Mancano le colonne obbligatorie: indirizzo' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(((await esito) as Error).message).toBe('Mancano le colonne obbligatorie: indirizzo');
  });

  it('non chiama il server se il rinnovo del token fallisce', async () => {
    token.and.rejectWith(new Error('refresh scaduto'));

    const errore = await firstValueFrom(service.importa(file, true)).catch((e: Error) => e);

    http.expectNone(() => true);
    expect((errore as Error).message).toContain('sessione è scaduta');
  });

  it("chiede l'anagrafica di presenze con il token e la modalità prova", async () => {
    const esito = firstValueFrom(service.sincronizza(true));
    await Promise.resolve();
    await Promise.resolve();
    const chiamata = http.expectOne((r) => r.url === `${environment.apiUrl}/api/admin/anagrafica`);

    expect(chiamata.request.method).toBe('POST');
    expect(chiamata.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    expect(chiamata.request.params.get('prova')).toBe('true');
    chiamata.flush({ prova: true, inserite: 3 });

    expect(await esito).toEqual(jasmine.objectContaining({ inserite: 3 }));
  });

  it("mostra il motivo quando presenze non risponde", async () => {
    const esito = firstValueFrom(service.sincronizza(false)).catch((e: Error) => e);
    await Promise.resolve();
    await Promise.resolve();
    http
      .expectOne((r) => r.url === `${environment.apiUrl}/api/admin/anagrafica`)
      .flush(
        { errore: "L'anagrafica di presenze non risponde: riprova tra poco" },
        { status: 502, statusText: 'Bad Gateway' },
      );

    expect(((await esito) as Error).message).toBe(
      "L'anagrafica di presenze non risponde: riprova tra poco",
    );
  });
});
