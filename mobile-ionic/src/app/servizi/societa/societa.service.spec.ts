import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { SocietaService } from './societa.service';

describe('SocietaService', () => {
  let service: SocietaService;
  let http: HttpTestingController;
  const base = `${environment.apiUrl}/api/societa`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(SocietaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('non chiama l API se il termine è vuoto', () => {
    let risultati: unknown;
    service.cerca('   ').subscribe((dati) => (risultati = dati));

    http.expectNone(() => true);
    expect(risultati).toEqual([]);
  });

  it('cerca il termine ripulito dagli spazi', () => {
    service.cerca('  certosa ').subscribe();

    const richiesta = http.expectOne((r) => r.url === base);
    expect(richiesta.request.params.get('nome')).toBe('certosa');
    richiesta.flush([]);
  });

  it('richiede tutti i campi', () => {
    service.tutti().subscribe();

    http.expectOne(`${environment.apiUrl}/api/campi`).flush([]);
  });

  it('richiede le partite sul campo della società', () => {
    service.partite('a/b').subscribe();

    http.expectOne(`${base}/a%2Fb/partite`).flush([]);
  });

  it('richiede le partite di tutti i campi con una chiamata sola', () => {
    service.partiteSuiCampi().subscribe();

    http.expectOne(`${environment.apiUrl}/api/campi/partite`).flush({});
  });

  it('richiede la scheda della società per id', () => {
    service.perId('1').subscribe();

    http.expectOne(`${base}/1`).flush({ id: '1' });
  });

  it('invia il nuovo campo in POST', () => {
    const campo = {
      nomeSocieta: 'A.S.D. Prova',
      nomeImpianto: 'Campo di Prova',
      indirizzoImpianto: 'Via di Prova 1',
    };

    service.inserisci(campo).subscribe();

    const richiesta = http.expectOne(base);
    expect(richiesta.request.method).toBe('POST');
    expect(richiesta.request.body).toEqual(campo);
    richiesta.flush({ id: 'nuovo', ...campo });
  });

  it('richiede le squadre della società', () => {
    service.squadre('abc 1').subscribe();

    http.expectOne(`${base}/abc%201/squadre`).flush([]);
  });

  describe('accesso del club', () => {
    it('legge alias e stemma di un club cliente', () => {
      let risposta: unknown;
      service.accesso('abc 1').subscribe((dati) => (risposta = dati));

      http
        .expectOne(`${base}/abc%201/accesso`)
        .flush({ alias: 'boreale', stemma: '/api/organizzazioni/3/logo' });
      expect(risposta).toEqual({ alias: 'boreale', stemma: '/api/organizzazioni/3/logo' });
    });

    it('204 per chi non è cliente vale nessun accesso', () => {
      let risposta: unknown = 'non arrivata';
      service.accesso('abc').subscribe((dati) => (risposta = dati));

      http.expectOne(`${base}/abc/accesso`).flush(null, { status: 204, statusText: 'No Content' });
      expect(risposta).toBeNull();
    });

    it('un errore non si propaga: nessun accesso', () => {
      let risposta: unknown = 'non arrivata';
      let errore = false;
      service.accesso('abc').subscribe({ next: (dati) => (risposta = dati), error: () => (errore = true) });

      http.expectOne(`${base}/abc/accesso`).flush('x', { status: 502, statusText: 'Bad Gateway' });
      expect(errore).toBe(false);
      expect(risposta).toBeNull();
    });
  });

  it('richiede gli ultimi risultati della società', () => {
    let risposta: unknown;
    service.risultati('abc 1').subscribe((dati) => (risposta = dati));

    http.expectOne(`${base}/abc%201/risultati`).flush({ stato: 'DISPONIBILI', risultati: [] });
    expect(risposta).toEqual({ stato: 'DISPONIBILI', risultati: [] });
  });
});
