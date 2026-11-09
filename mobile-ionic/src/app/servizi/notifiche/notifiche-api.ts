import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ElencoProssime } from '../../modelli/prossime';

/** Il corpo di «prossime»: cosa il server deve sapere per rispondere. */
export interface RichiestaProssime {
  squadre: string[];
  posizione: { lat: number; lng: number } | null;
  raggioKm: number;
  endpoint: string | null;
}

/** L'iscrizione e le preferenze che il server conserva per un dispositivo. */
export interface CorpoIscrizione {
  squadre: string[];
  avvisoSquadre: boolean;
  avvisoVicino: boolean;
  posizione: { lat: number; lng: number } | null;
  raggioKm: number;
  [campo: string]: unknown;
}

/** Le chiamate HTTP del backend delle notifiche, senza logica di pagina. */
@Injectable({ providedIn: 'root' })
export class NotificheApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/notifiche`;

  async chiaveVapid(): Promise<string> {
    const { chiave } = await firstValueFrom(
      this.http.get<{ chiave: string }>(`${this.base}/chiave`),
    );
    return chiave;
  }

  salvaIscrizione(corpo: CorpoIscrizione): Promise<unknown> {
    return firstValueFrom(this.http.put(`${this.base}/iscrizione`, corpo));
  }

  cancellaIscrizione(endpoint: string): Promise<unknown> {
    return firstValueFrom(this.http.delete(`${this.base}/iscrizione`, { body: { endpoint } }));
  }

  prossime(richiesta: RichiestaProssime): Promise<ElencoProssime> {
    return firstValueFrom(this.http.post<ElencoProssime>(`${this.base}/prossime`, richiesta));
  }
}
