import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { Prospect } from '../modelli/prospect';
import { AutenticazioneService } from './autenticazione.service';
import { messaggioErrore } from './errori-amministrazione';

/** Come sta l'importazione dal registro (GET /api/admin/prospect/importazione). */
export interface StatoImportazioneProspect {
  inCorso: boolean;
  ultimo: {
    stato: string;
    iniziato?: string;
    finito?: string;
    pagine: number;
    lette: number;
    nuove: number;
    dettagli: number;
    regioneCorrente?: string;
    errore?: string;
    collegamento?: { prospectCalcioLazio: number; collegati: number; conCoordinate: number };
  };
  regioni: { regioneId: number; regione: string; prossimoStart: number; totale?: number; completata: boolean }[];
}

/**
 * La lista delle ASD/SSD per chi amministra: tutte le chiamate portano il
 * token di Keycloak, e il backend le apre solo al ruolo trovacampo-admin.
 */
@Injectable({ providedIn: 'root' })
export class ProspectService {
  private readonly http = inject(HttpClient);
  private readonly autenticazione = inject(AutenticazioneService);
  private readonly url = `${environment.apiUrl}/api/admin/prospect`;

  private conToken<T>(chiamata: (headers: HttpHeaders) => Observable<T>): Observable<T> {
    return from(this.autenticazione.token()).pipe(
      switchMap((token) => chiamata(new HttpHeaders({ Authorization: `Bearer ${token}` }))),
      catchError((errore: unknown) => throwError(() => new Error(messaggioErrore(errore)))),
    );
  }

  /** I prospect con le coordinate, per la mappa. */
  sullaMappa(): Observable<Prospect[]> {
    return this.conToken((headers) => this.http.get<Prospect[]>(this.url, { headers }));
  }

  stato(): Observable<StatoImportazioneProspect> {
    return this.conToken((headers) =>
      this.http.get<StatoImportazioneProspect>(`${this.url}/importazione`, { headers }),
    );
  }

  /** Avvia l'importazione in sottofondo; `regioni` vuoto vuol dire tutta Italia. */
  avvia(regioni: number[], maxPagine?: number): Observable<StatoImportazioneProspect> {
    let params = new HttpParams();
    regioni.forEach((r) => (params = params.append('regioni', String(r))));
    if (maxPagine) {
      params = params.set('maxPagine', String(maxPagine));
    }
    return this.conToken((headers) =>
      this.http.post<StatoImportazioneProspect>(`${this.url}/importazione`, null, { headers, params }),
    );
  }

  ferma(): Observable<StatoImportazioneProspect> {
    return this.conToken((headers) =>
      this.http.post<StatoImportazioneProspect>(`${this.url}/importazione/ferma`, null, { headers }),
    );
  }

  /** prospect_italia.xlsx, come file da salvare. */
  esporta(): Observable<Blob> {
    return this.conToken((headers) =>
      this.http.get(`${this.url}/export.xlsx`, { headers, responseType: 'blob' }),
    );
  }
}
