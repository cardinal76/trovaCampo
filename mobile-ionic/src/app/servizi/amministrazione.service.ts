import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { Esclusione, ModificaSocieta, Societa } from '../modelli/societa';
import { AutenticazioneService } from './autenticazione.service';
import { CampiCambiatiService } from './campi-cambiati.service';
import { messaggioErrore } from './errori-amministrazione';

/** Creazione, modifica e cancellazione delle schede, per chi ha il ruolo trovacampo-admin. */
@Injectable({ providedIn: 'root' })
export class AmministrazioneService {
  private readonly http = inject(HttpClient);
  private readonly autenticazione = inject(AutenticazioneService);
  private readonly cambiati = inject(CampiCambiatiService);
  private readonly base = `${environment.apiUrl}/api/admin/societa`;
  private readonly baseEsclusioni = `${environment.apiUrl}/api/admin/esclusioni`;

  // In caso di errore gli Observable falliscono con un messaggio già pronto da mostrare.

  // Dopo ogni scrittura riuscita elenco, mappa e ricerca ricaricano al rientro.

  modifica(id: string, scheda: ModificaSocieta): Observable<Societa> {
    return this.conToken((headers) =>
      this.http.put<Societa>(`${this.base}/${encodeURIComponent(id)}`, scheda, { headers }),
    ).pipe(tap(() => this.cambiati.segnala()));
  }

  crea(scheda: ModificaSocieta): Observable<Societa> {
    return this.conToken((headers) => this.http.post<Societa>(this.base, scheda, { headers })).pipe(
      tap(() => this.cambiati.segnala()),
    );
  }

  /**
   * Una scheda che viene da presenze resta esclusa dalla sincronizzazione:
   * non ricompare al giro dopo. Si annulla con {@link annullaEsclusione}.
   */
  elimina(id: string): Observable<void> {
    return this.conToken((headers) =>
      this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`, { headers }),
    ).pipe(tap(() => this.cambiati.segnala()));
  }

  /** I campi di presenze eliminati, i più recenti prima. */
  esclusioni(): Observable<Esclusione[]> {
    return this.conToken((headers) => this.http.get<Esclusione[]>(this.baseEsclusioni, { headers }));
  }

  /** Il campo torna con la prossima sincronizzazione, se presenze lo manda ancora. */
  annullaEsclusione(id: string): Observable<void> {
    return this.conToken((headers) =>
      this.http.delete<void>(`${this.baseEsclusioni}/${encodeURIComponent(id)}`, { headers }),
    );
  }

  private conToken<T>(chiamata: (headers: HttpHeaders) => Observable<T>): Observable<T> {
    return from(this.autenticazione.token()).pipe(
      switchMap((token) => chiamata(new HttpHeaders({ Authorization: `Bearer ${token}` }))),
      catchError((errore: unknown) => throwError(() => new Error(messaggioErrore(errore)))),
    );
  }
}
