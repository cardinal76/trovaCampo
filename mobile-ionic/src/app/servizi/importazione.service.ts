import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { EsitoImportazione } from '../modelli/importazione';
import { AutenticazioneService } from './autenticazione.service';
import { messaggioErrore } from './errori-amministrazione';

@Injectable({ providedIn: 'root' })
export class ImportazioneService {
  private readonly http = inject(HttpClient);
  private readonly autenticazione = inject(AutenticazioneService);
  private readonly url = `${environment.apiUrl}/api/admin/importazione`;

  /**
   * Carica il file Excel con il token di chi è entrato. Con `prova` il server
   * non salva niente e restituisce solo cosa succederebbe. In caso di errore
   * l'Observable fallisce con un messaggio già pronto da mostrare.
   */
  importa(file: File, prova: boolean): Observable<EsitoImportazione> {
    const corpo = new FormData();
    corpo.append('file', file, file.name);

    return from(this.autenticazione.token()).pipe(
      switchMap((token) =>
        this.http.post<EsitoImportazione>(this.url, corpo, {
          headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
          params: new HttpParams().set('prova', String(prova)),
        }),
      ),
      catchError((errore: unknown) => throwError(() => new Error(messaggioErrore(errore)))),
    );
  }
}
