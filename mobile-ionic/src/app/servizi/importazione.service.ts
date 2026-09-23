import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { EsitoImportazione } from '../modelli/importazione';
import { AutenticazioneService, RUOLO_AMMINISTRATORE } from './autenticazione.service';

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
      catchError((errore: unknown) => throwError(() => new Error(messaggio(errore)))),
    );
  }
}

function messaggio(errore: unknown): string {
  if (!(errore instanceof HttpErrorResponse)) {
    // Il rinnovo del token è fallito: la sessione su Keycloak è finita.
    return 'La sessione è scaduta: ricarica la pagina per rientrare.';
  }
  switch (errore.status) {
    case 0:
      return 'Server non raggiungibile. Controlla la connessione e riprova.';
    case 401:
      return 'La sessione è scaduta: ricarica la pagina per rientrare.';
    case 403:
      return `Il tuo utente non ha il ruolo ${RUOLO_AMMINISTRATORE}.`;
    case 413:
      return 'Il file supera i 20 MB.';
    default:
      // 400 e simili: il backend spiega cosa non va nel file, es. le colonne mancanti.
      return errore.error?.errore ?? `Errore del server (${errore.status}).`;
  }
}
