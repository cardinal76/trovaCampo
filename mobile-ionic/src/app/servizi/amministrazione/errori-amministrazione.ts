import { HttpErrorResponse } from '@angular/common/http';
import { RUOLO_AMMINISTRATORE } from './autenticazione.service';

/**
 * Il messaggio da mostrare per un errore delle chiamate di amministrazione
 * (importazione, modifica della scheda): stesso login, stessi casi.
 */
export function messaggioErrore(errore: unknown): string {
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
    case 404:
      return 'La società non esiste più: forse è stata rimossa nel frattempo.';
    case 413:
      return 'Il file supera i 20 MB.';
    default:
      // 400 e simili: il backend spiega cosa non va, es. un campo obbligatorio vuoto.
      return errore.error?.errore ?? `Errore del server (${errore.status}).`;
  }
}
