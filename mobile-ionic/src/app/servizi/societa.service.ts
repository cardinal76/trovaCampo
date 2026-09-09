import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { NuovoCampo, Societa } from '../modelli/societa';

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

  /** Funzioni 2 e 3: anagrafica e campionati della società. */
  perId(id: string): Observable<Societa> {
    return this.http.get<Societa>(`${this.base}/${encodeURIComponent(id)}`);
  }

  inserisci(campo: NuovoCampo): Observable<Societa> {
    return this.http.post<Societa>(this.base, campo);
  }
}
