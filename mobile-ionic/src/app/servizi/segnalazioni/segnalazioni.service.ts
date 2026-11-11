import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AutenticazioneService } from '../amministrazione/autenticazione.service';
import { messaggioErrore } from '../amministrazione/errori-amministrazione';

export type TipoRettifica = 'NOME_SOCIETA' | 'INDIRIZZO_CAMPO' | 'ALTRO';

export interface Segnalazione {
  tipo: TipoRettifica;
  societaId?: string;
  attuale?: string;
  corretto: string;
  contatto?: string;
  /** Casella-trappola: resta sempre vuota per una persona. */
  sito?: string;
}

/** Una segnalazione come la dà l'amministrazione. */
export interface SegnalazioneRicevuta extends Segnalazione {
  id: string;
  ricevutaIl: string;
  gestita: boolean;
}

/** «Hai dati da rettificare?»: l'invio è pubblico, senza login. */
@Injectable({ providedIn: 'root' })
export class SegnalazioniService {
  private readonly http = inject(HttpClient);

  invia(segnalazione: Segnalazione): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/api/segnalazioni`, segnalazione);
  }

  private readonly autenticazione = inject(AutenticazioneService);
  private readonly urlAdmin = `${environment.apiUrl}/api/admin/segnalazioni`;

  private conToken<T>(chiamata: (headers: HttpHeaders) => Observable<T>): Observable<T> {
    return from(this.autenticazione.token()).pipe(
      switchMap((token) => chiamata(new HttpHeaders({ Authorization: `Bearer ${token}` }))),
      catchError((errore: unknown) => throwError(() => new Error(messaggioErrore(errore)))),
    );
  }

  /** Solo per chi amministra: dalla più recente. */
  elenco(): Observable<SegnalazioneRicevuta[]> {
    return this.conToken((headers) =>
      this.http.get<SegnalazioneRicevuta[]>(this.urlAdmin, { headers }),
    );
  }

  segnaGestita(id: string, gestita: boolean): Observable<SegnalazioneRicevuta> {
    return this.conToken((headers) =>
      this.http.put<SegnalazioneRicevuta>(
        `${this.urlAdmin}/${encodeURIComponent(id)}`,
        { gestita },
        { headers },
      ),
    );
  }
}
