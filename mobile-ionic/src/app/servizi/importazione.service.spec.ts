import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { INTESTAZIONE_TOKEN, ImportazioneService } from './importazione.service';

describe('ImportazioneService', () => {
  let service: ImportazioneService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/api/admin/importazione`;
  const file = new File(['contenuto'], 'campi.xlsx');

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ImportazioneService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('invia il file con il token e la modalità prova', () => {
    service.importa(file, '  segreto  ', true).subscribe();

    const richiesta = http.expectOne((r) => r.url === url);
    expect(richiesta.request.method).toBe('POST');
    expect(richiesta.request.headers.get(INTESTAZIONE_TOKEN)).toBe('segreto');
    expect(richiesta.request.params.get('prova')).toBe('true');
    expect((richiesta.request.body as FormData).get('file')).toEqual(jasmine.any(File));
    richiesta.flush({});
  });

  it('traduce un token errato in un messaggio', () => {
    let messaggio = '';
    service.importa(file, 'x', false).subscribe({ error: (e: Error) => (messaggio = e.message) });

    http.expectOne((r) => r.url === url).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(messaggio).toBe('Token errato.');
  });

  it("dice quando l'importazione è spenta sul server", () => {
    let messaggio = '';
    service.importa(file, 'x', true).subscribe({ error: (e: Error) => (messaggio = e.message) });

    http.expectOne((r) => r.url === url).flush({}, { status: 404, statusText: 'Not Found' });

    expect(messaggio).toContain('IMPORTAZIONE_TOKEN');
  });

  it('mostra il motivo dato dal server per un file non valido', () => {
    let messaggio = '';
    service.importa(file, 'x', true).subscribe({ error: (e: Error) => (messaggio = e.message) });

    http
      .expectOne((r) => r.url === url)
      .flush(
        { errore: 'Mancano le colonne obbligatorie: indirizzo' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(messaggio).toBe('Mancano le colonne obbligatorie: indirizzo');
  });
});
