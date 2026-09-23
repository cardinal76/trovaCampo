import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { ModificaSocieta } from '../modelli/societa';
import { AmministrazioneService } from './amministrazione.service';
import { AutenticazioneService } from './autenticazione.service';

describe('AmministrazioneService', () => {
  let service: AmministrazioneService;
  let http: HttpTestingController;
  const scheda = {
    siglaSocieta: 'A.S.D.',
    nomeSocieta: 'Certosa Calcio',
    comitatoRegionale: '',
    nomeImpianto: 'Campo Certosa',
    indirizzoImpianto: 'Via della Certosa 12',
    localitaImpianto: 'Roma',
    provinciaImpianto: 'RM',
  } as ModificaSocieta;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AutenticazioneService, useValue: { token: () => Promise.resolve('abc') } },
      ],
    });
    service = TestBed.inject(AmministrazioneService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function richiesta(id: string) {
    const esito = firstValueFrom(service.modifica(id, scheda)).catch((e: Error) => e);
    await Promise.resolve();
    await Promise.resolve();
    return { esito, chiamata: http.expectOne(`${environment.apiUrl}/api/admin/societa/${id}`) };
  }

  it('manda la scheda con il token di Keycloak', async () => {
    const { esito, chiamata } = await richiesta('1');

    expect(chiamata.request.method).toBe('PUT');
    expect(chiamata.request.headers.get('Authorization')).toBe('Bearer abc');
    expect(chiamata.request.body).toEqual(scheda);
    chiamata.flush({ id: '1', ...scheda });

    expect(await esito).toEqual(jasmine.objectContaining({ id: '1' }));
  });

  it('mostra il motivo dato dal server per dati non validi', async () => {
    const { esito, chiamata } = await richiesta('1');
    chiamata.flush({ errore: 'email non valida' }, { status: 400, statusText: 'Bad Request' });

    expect(((await esito) as Error).message).toBe('email non valida');
  });

  it('dice quando manca il ruolo', async () => {
    const { esito, chiamata } = await richiesta('1');
    chiamata.flush({}, { status: 403, statusText: 'Forbidden' });

    expect(((await esito) as Error).message).toContain('trovacampo-admin');
  });

  it('crea una società nuova', async () => {
    const esito = firstValueFrom(service.crea(scheda));
    await Promise.resolve();
    await Promise.resolve();
    const chiamata = http.expectOne(`${environment.apiUrl}/api/admin/societa`);

    expect(chiamata.request.method).toBe('POST');
    expect(chiamata.request.headers.get('Authorization')).toBe('Bearer abc');
    chiamata.flush({ id: 'nuovo', ...scheda }, { status: 201, statusText: 'Created' });

    expect((await esito).id).toBe('nuovo');
  });

  it('elimina una società', async () => {
    const esito = firstValueFrom(service.elimina('1'), { defaultValue: undefined });
    await Promise.resolve();
    await Promise.resolve();
    const chiamata = http.expectOne(`${environment.apiUrl}/api/admin/societa/1`);

    expect(chiamata.request.method).toBe('DELETE');
    chiamata.flush(null, { status: 204, statusText: 'No Content' });

    await esito;
  });
});
