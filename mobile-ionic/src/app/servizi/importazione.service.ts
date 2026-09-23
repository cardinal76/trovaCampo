import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { EsitoImportazione } from '../modelli/importazione';

/** Intestazione letta da ImportazioneController nel backend. */
export const INTESTAZIONE_TOKEN = 'X-Token-Importazione';

@Injectable({ providedIn: 'root' })
export class ImportazioneService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/api/admin/importazione`;

  /**
   * Carica il file Excel. Con `prova` il server non salva niente e restituisce
   * solo cosa succederebbe. In caso di errore l'Observable fallisce con un
   * messaggio già pronto da mostrare.
   */
  importa(file: File, token: string, prova: boolean): Observable<EsitoImportazione> {
    const corpo = new FormData();
    corpo.append('file', file, file.name);

    return this.http
      .post<EsitoImportazione>(this.url, corpo, {
        headers: new HttpHeaders({ [INTESTAZIONE_TOKEN]: token.trim() }),
        params: new HttpParams().set('prova', String(prova)),
      })
      .pipe(catchError((errore: HttpErrorResponse) => throwError(() => new Error(messaggio(errore)))));
  }
}

function messaggio(errore: HttpErrorResponse): string {
  switch (errore.status) {
    case 0:
      return 'Server non raggiungibile. Controlla la connessione e riprova.';
    case 401:
      return 'Token errato.';
    case 404:
      return "L'importazione non è attiva sul server: manca IMPORTAZIONE_TOKEN in .env.prod.";
    case 413:
      return 'Il file supera i 20 MB.';
    default:
      // 400 e simili: il backend spiega cosa non va nel file, es. le colonne mancanti.
      return errore.error?.errore ?? `Errore del server (${errore.status}).`;
  }
}
