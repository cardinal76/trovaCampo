import { Injectable, signal } from '@angular/core';
import { TUTTE } from '../../modelli/provincia';
import { archivioLocale } from '../archivio-locale';

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
    // Con l'archivio bloccato la scelta vale finché la pagina resta aperta.
    archivioLocale.scrivi(CHIAVE, provincia);
  }
}

function ricordata(): string {
  return archivioLocale.leggi(CHIAVE) || TUTTE;
}
