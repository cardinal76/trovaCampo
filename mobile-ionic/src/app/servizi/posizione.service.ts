import { Injectable } from '@angular/core';
import { Posizione } from '../modelli/vicini';

/** Perché la posizione non è arrivata, con il messaggio da mostrare. */
export class ErrorePosizione extends Error {
  constructor(
    readonly motivo:
      | 'non-supportata'
      | 'non-sicura'
      | 'negata'
      | 'vietata'
      | 'non-disponibile'
      | 'scaduta',
    messaggio: string,
  ) {
    super(messaggio);
  }
}

/**
 * Il messaggio del browser quando a negare la posizione non è l'utente ma
 * la Permissions-Policy mandata dal server: Chrome scrive "...disabled in
 * this document by permissions policy", i più vecchi "feature policy".
 */
const VIETATA_DAL_SITO = /permissions?[ -]policy|feature[ -]policy/i;

/** Il messaggio per ciascun codice di GeolocationPositionError (1, 2, 3). */
export function errorePerCodice(codice: number, messaggioBrowser = ''): ErrorePosizione {
  switch (codice) {
    case 1:
      if (VIETATA_DAL_SITO.test(messaggioBrowser)) {
        return new ErrorePosizione(
          'vietata',
          'La posizione è disattivata dal sito stesso, non dal tuo telefono: i permessi ' +
            'che dai non cambiano niente finché non viene corretto.',
        );
      }
      return new ErrorePosizione(
        'negata',
        'Non hai dato il permesso di usare la posizione. Puoi concederlo dalle impostazioni ' +
          'del sito nel browser (o dell’app sul telefono) e riprovare.',
      );
    case 3:
      return new ErrorePosizione(
        'scaduta',
        'La posizione non è arrivata in tempo. Riprova, magari all’aperto o con il GPS attivo.',
      );
    default:
      return new ErrorePosizione(
        'non-disponibile',
        'Il dispositivo non riesce a stabilire dove ti trovi. Controlla che la ' +
          'localizzazione sia attiva e riprova.',
      );
  }
}

/**
 * La posizione attuale, dal browser o dal telefono.
 *
 * Usa navigator.geolocation anche nell'app: nella WebView di Capacitor è la
 * stessa API, che chiede il permesso al sistema operativo, e così non serve
 * un plugin nativo in più. Il servizio esiste per poterlo sostituire nei test.
 */
@Injectable({ providedIn: 'root' })
export class PosizioneService {
  attuale(): Promise<Posizione> {
    // I browser danno la posizione solo alle pagine in HTTPS (o su localhost):
    // meglio dirlo subito che lasciar credere a un permesso negato.
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      return Promise.reject(
        new ErrorePosizione(
          'non-sicura',
          'Il browser dà la posizione solo alle pagine aperte in HTTPS. ' +
            'Apri il sito con https:// e riprova.',
        ),
      );
    }
    const geolocalizzazione = typeof navigator !== 'undefined' ? navigator.geolocation : undefined;
    if (!geolocalizzazione) {
      return Promise.reject(
        new ErrorePosizione(
          'non-supportata',
          'Questo browser non sa dare la posizione. Prova con un altro browser o dal telefono.',
        ),
      );
    }
    return new Promise((risolvi, rifiuta) => {
      geolocalizzazione.getCurrentPosition(
        (p) => risolvi({ lat: p.coords.latitude, lng: p.coords.longitude }),
        (errore) => rifiuta(errorePerCodice(errore.code, errore.message)),
        // Per scegliere il campo più vicino basta una posizione di qualche
        // minuto fa; il GPS preciso serve, ma senza aspettarlo all'infinito.
        { enableHighAccuracy: true, timeout: 15000, maximumAge: 120000 },
      );
    });
  }
}
