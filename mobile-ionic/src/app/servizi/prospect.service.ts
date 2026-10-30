import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { FonteIndirizzo, Prospect } from '../modelli/prospect';
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
 * Quante società di calcio del Lazio hanno l'indirizzo del campo
 * (GET /api/admin/prospect/copertura e POST /api/admin/prospect/collegamento).
 */
export interface CoperturaProspect {
  prospectCalcioLazio: number;
  collegati: number;
  conCoordinate: number;
  copertura: number;
  perFonte: Partial<Record<FonteIndirizzo, number>>;
  perConfidenza: Partial<Record<'ALTA' | 'MEDIA' | 'BASSA', number>>;
}

/** La ricerca degli indirizzi con OpenStreetMap, in sottofondo. */
export interface StatoGeocodifica {
  inCorso: boolean;
  daCercare: number;
  cercate: number;
  trovate: number;
  errori: number;
  messaggio: string;
}

export interface AvvioGeocodifica {
  avviato: boolean;
  messaggio: string;
  stato: StatoGeocodifica;
}

/** Il censimento degli impianti open data appena importato. */
export interface EsitoOpenData {
  fonte: string;
  licenza: string;
  righe: number;
  impianti: number;
  conCalcioA11: number;
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

  copertura(): Observable<CoperturaProspect> {
    return this.conToken((headers) =>
      this.http.get<CoperturaProspect>(`${this.url}/copertura`, { headers }),
    );
  }

  /** Il collegamento ai campi LND e al censimento open data, subito (senza OpenStreetMap). */
  collega(): Observable<CoperturaProspect> {
    return this.conToken((headers) =>
      this.http.post<CoperturaProspect>(`${this.url}/collegamento`, null, { headers }),
    );
  }

  /** La ricerca su OpenStreetMap per chi è ancora senza indirizzo, in sottofondo. */
  geocodifica(): Observable<AvvioGeocodifica> {
    return this.conToken((headers) =>
      this.http.post<AvvioGeocodifica>(`${this.url}/geocodifica`, null, { headers }),
    );
  }

  statoGeocodifica(): Observable<StatoGeocodifica> {
    return this.conToken((headers) =>
      this.http.get<StatoGeocodifica>(`${this.url}/geocodifica`, { headers }),
    );
  }

  /** (Re)importa il censimento degli impianti sportivi in open data. */
  importaOpenData(): Observable<EsitoOpenData> {
    return this.conToken((headers) =>
      this.http.post<EsitoOpenData>(
        `${environment.apiUrl}/api/admin/indirizzi/impianti-open-data`,
        null,
        { headers },
      ),
    );
  }

  /** prospect_italia.xlsx, come file da salvare. */
  esporta(): Observable<Blob> {
    return this.conToken((headers) =>
      this.http.get(`${this.url}/export.xlsx`, { headers, responseType: 'blob' }),
    );
  }
}
