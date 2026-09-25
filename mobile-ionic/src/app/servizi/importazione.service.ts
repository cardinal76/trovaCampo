import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { EsitoImportazione } from '../modelli/importazione';
import { AutenticazioneService } from './autenticazione.service';
import { CampiCambiatiService } from './campi-cambiati.service';
import { messaggioErrore } from './errori-amministrazione';

@Injectable({ providedIn: 'root' })
export class ImportazioneService {
  private readonly http = inject(HttpClient);
  private readonly autenticazione = inject(AutenticazioneService);
  private readonly cambiati = inject(CampiCambiatiService);
  private readonly url = `${environment.apiUrl}/api/admin/importazione`;

  /**
   * I campi dell'anagrafica di presenze (società e campi letti dai Comunicati
   * Ufficiali), adesso invece che al prossimo giro programmato. Stesso esito
   * e stessa prova di un file.
   */
  sincronizza(prova: boolean): Observable<EsitoImportazione> {
    return from(this.autenticazione.token()).pipe(
      switchMap((token) =>
        this.http.post<EsitoImportazione>(`${environment.apiUrl}/api/admin/anagrafica`, null, {
          headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
          params: new HttpParams().set('prova', String(prova)),
        }),
      ),
      // Un'importazione vera cambia l'archivio: elenco e mappa ricaricano al rientro.
      tap(() => {
        if (!prova) {
          this.cambiati.segnala();
        }
      }),
      catchError((errore: unknown) => throwError(() => new Error(messaggioErrore(errore)))),
    );
  }

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
      // Un'importazione vera cambia l'archivio: elenco e mappa ricaricano al rientro.
      tap(() => {
        if (!prova) {
          this.cambiati.segnala();
        }
      }),
      catchError((errore: unknown) => throwError(() => new Error(messaggioErrore(errore)))),
    );
  }
}
