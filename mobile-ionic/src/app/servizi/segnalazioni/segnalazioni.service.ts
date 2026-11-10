import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

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

/** «Hai dati da rettificare?»: l'invio è pubblico, senza login. */
@Injectable({ providedIn: 'root' })
export class SegnalazioniService {
  private readonly http = inject(HttpClient);

  invia(segnalazione: Segnalazione): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/api/segnalazioni`, segnalazione);
  }
}
