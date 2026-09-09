import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
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
});
