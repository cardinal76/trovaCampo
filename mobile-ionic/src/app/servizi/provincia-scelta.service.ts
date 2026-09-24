import { Injectable, signal } from '@angular/core';
import { TUTTE } from '../modelli/provincia';

const CHIAVE = 'trovacampo.provincia';

/**
 * La provincia scelta nel filtro di elenco e mappa.
 *
 * Un servizio solo per le due pagine: chi sceglie Latina nell'elenco e
 * passa alla mappa si aspetta di vedere Latina anche lì. Resta anche fra
 * una visita e l'altra, perché di solito si cercano sempre i campi della
 * propria zona.
 */
@Injectable({ providedIn: 'root' })
export class ProvinciaSceltaService {
  readonly scelta = signal(ricordata());

  scegli(provincia: string): void {
    this.scelta.set(provincia);
    try {
      localStorage.setItem(CHIAVE, provincia);
    } catch {
      // Navigazione privata o archivio bloccato: vale finché la pagina resta aperta.
    }
  }
}

function ricordata(): string {
  try {
    return localStorage.getItem(CHIAVE) || TUTTE;
  } catch {
    return TUTTE;
  }
}
