import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { Partita, PartitePerCampo } from '../modelli/partita';
import { NuovoCampo, Societa } from '../modelli/societa';
import { Squadra } from '../modelli/squadra';

@Injectable({ providedIn: 'root' })
export class SocietaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/societa`;

  /** Funzione 1: ricerca per nome società, nome impianto, indirizzo o località. */
  cerca(nome: string): Observable<Societa[]> {
    const termine = nome.trim();

    if (termine.length === 0) {
      return of([]);
    }

    return this.http.get<Societa[]>(this.base, { params: new HttpParams().set('nome', termine) });
  }

  /** Tutti i campi, in ordine di nome, per l'elenco e la mappa completi. */
  tutti(): Observable<Societa[]> {
    return this.http.get<Societa[]>(`${environment.apiUrl}/api/campi`);
  }

  /** Funzioni 2 e 3: anagrafica e campionati della società. */
  perId(id: string): Observable<Societa> {
    return this.http.get<Societa>(`${this.base}/${encodeURIComponent(id)}`);
  }

  /** Le squadre della società con il campionato di ognuna, dall'anagrafica di presenze. */
  squadre(id: string): Observable<Squadra[]> {
    return this.http.get<Squadra[]>(`${this.base}/${encodeURIComponent(id)}/squadre`);
  }

  /** Le prossime partite sul campo della società, dal calendario di presenze. */
  partite(id: string): Observable<Partita[]> {
    return this.http.get<Partita[]>(`${this.base}/${encodeURIComponent(id)}/partite`);
  }

  /**
   * Per la mappa, con una chiamata sola: le prime partite della settimana di
   * ogni campo, per id del campo in presenze.
   */
  partiteSuiCampi(): Observable<PartitePerCampo> {
    return this.http.get<PartitePerCampo>(`${environment.apiUrl}/api/campi/partite`);
  }

  inserisci(campo: NuovoCampo): Observable<Societa> {
    return this.http.post<Societa>(this.base, campo);
  }
}
