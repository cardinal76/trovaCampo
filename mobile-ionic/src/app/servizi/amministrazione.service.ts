import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { ModificaSocieta, Societa } from '../modelli/societa';
import { AutenticazioneService } from './autenticazione.service';
import { messaggioErrore } from './errori-amministrazione';

/** Modifica della scheda di una società, per chi ha il ruolo trovacampo-admin. */
@Injectable({ providedIn: 'root' })
export class AmministrazioneService {
  private readonly http = inject(HttpClient);
  private readonly autenticazione = inject(AutenticazioneService);
  private readonly base = `${environment.apiUrl}/api/admin/societa`;

  /** In caso di errore l'Observable fallisce con un messaggio già pronto da mostrare. */
  modifica(id: string, scheda: ModificaSocieta): Observable<Societa> {
    return from(this.autenticazione.token()).pipe(
      switchMap((token) =>
        this.http.put<Societa>(`${this.base}/${encodeURIComponent(id)}`, scheda, {
          headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
        }),
      ),
      catchError((errore: unknown) => throwError(() => new Error(messaggioErrore(errore)))),
    );
  }
}
