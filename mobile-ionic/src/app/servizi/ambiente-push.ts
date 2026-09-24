import { Injectable } from '@angular/core';

/**
 * Cosa sa fare questo browser con le notifiche push:
 *
 * - `supportato`: si può accendere tutto;
 * - `iphone-da-installare`: iPhone o iPad nel browser. Safari dà le notifiche
 *   solo al sito aggiunto alla schermata Home (iOS 16.4 e successivi) e
 *   aperto da lì;
 * - `app-nativa`: l'app Capacitor, dove le push del web non ci sono;
 * - `non-sicuro`: pagina non in HTTPS, dove il browser non le permette;
 * - `non-supportato`: un browser che non le ha (o un iPhone con iOS vecchio).
 */
export type SupportoPush =
  | 'supportato'
  | 'iphone-da-installare'
  | 'app-nativa'
  | 'non-sicuro'
  | 'non-supportato';

/** Il permesso di usare la posizione, senza chiederlo. */
export type PermessoPosizione = 'granted' | 'denied' | 'prompt' | 'sconosciuto';

/** Un'iscrizione push come la dà PushSubscription.toJSON(): quello che il backend vuole. */
export interface IscrizionePush {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

/**
 * Le API del browser per le notifiche (service worker, Push API, permessi),
 * riunite qui dietro metodi semplici: il resto dell'app non tocca navigator,
 * e i test le sostituiscono con una finta.
 */
@Injectable({ providedIn: 'root' })
export class AmbientePush {
  supporto(): SupportoPush {
    if (typeof window === 'undefined' || typeof navigator === 'undefined') {
      return 'non-supportato';
    }
    const capacitor = (window as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor;
    if (capacitor?.isNativePlatform?.()) {
      return 'app-nativa';
    }
    if (window.isSecureContext === false) {
      return 'non-sicuro';
    }
    const conPush = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
    // Su iPhone il PushManager compare solo nel sito aperto dalla Home: nel
    // Safari normale manca anche con iOS recente, e la cosa da dire è
    // "aggiungilo alla Home", non "il tuo browser non va".
    if (eIos() && !installato()) {
      return 'iphone-da-installare';
    }
    return conPush ? 'supportato' : 'non-supportato';
  }

  permesso(): NotificationPermission {
    return typeof Notification === 'undefined' ? 'denied' : Notification.permission;
  }

  /** Va chiamato dentro il tocco dell'utente: Safari rifiuta la richiesta altrimenti. */
  async chiediPermesso(): Promise<NotificationPermission> {
    if (typeof Notification === 'undefined') {
      return 'denied';
    }
    return Notification.requestPermission();
  }

  /**
   * Registra public/sw.js. `updateViaCache: 'none'`: il browser rilegge il
   * worker dalla rete a ogni controllo, qualunque cosa dica la cache HTTP.
   */
  async registra(): Promise<void> {
    await navigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' });
  }

  /**
   * L'iscrizione push di questo browser, fatta con la chiave VAPID del
   * backend. Se ce n'è già una con un'altra chiave (il backend ha cambiato
   * chiavi) la si rifà: con la vecchia i messaggi verrebbero rifiutati.
   */
  async iscrivi(chiavePubblica: string): Promise<IscrizionePush> {
    const registrazione = await navigator.serviceWorker.ready;
    const chiave = daBase64Url(chiavePubblica);
    let iscrizione = await registrazione.pushManager.getSubscription();
    if (iscrizione && !stessiByte(iscrizione.options.applicationServerKey, chiave)) {
      await iscrizione.unsubscribe();
      iscrizione = null;
    }
    iscrizione ??= await registrazione.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: chiave,
    });
    return comeJson(iscrizione);
  }

  /** Quella che c'è, senza crearne una: per cancellarla anche sul server. */
  async iscrizioneAttuale(): Promise<IscrizionePush | null> {
    const registrazione = await navigator.serviceWorker.getRegistration('/');
    const iscrizione = await registrazione?.pushManager.getSubscription();
    return iscrizione ? comeJson(iscrizione) : null;
  }

  async disiscrivi(): Promise<void> {
    const registrazione = await navigator.serviceWorker.getRegistration('/');
    const iscrizione = await registrazione?.pushManager.getSubscription();
    await iscrizione?.unsubscribe();
  }

  /**
   * Se la posizione si può leggere senza chiedere niente: all'apertura
   * dell'app si aggiorna solo così, mai con una richiesta a sorpresa.
   */
  async permessoPosizione(): Promise<PermessoPosizione> {
    try {
      const stato = await navigator.permissions.query({ name: 'geolocation' });
      return stato.state;
    } catch {
      // Safari vecchi non hanno navigator.permissions.
      return 'sconosciuto';
    }
  }
}

function eIos(): boolean {
  const agente = navigator.userAgent;
  // Gli iPad recenti si presentano come un Mac: li tradisce il touch.
  return /iPad|iPhone|iPod/.test(agente) || (/Macintosh/.test(agente) && navigator.maxTouchPoints > 1);
}

function installato(): boolean {
  return (
    window.matchMedia?.('(display-mode: standalone)').matches === true ||
    (navigator as { standalone?: boolean }).standalone === true
  );
}

function comeJson(iscrizione: PushSubscription): IscrizionePush {
  const json = iscrizione.toJSON();
  return {
    endpoint: json.endpoint ?? iscrizione.endpoint,
    keys: { p256dh: json.keys?.['p256dh'] ?? '', auth: json.keys?.['auth'] ?? '' },
  };
}

export function daBase64Url(testo: string): Uint8Array<ArrayBuffer> {
  const base64 = testo.replace(/-/g, '+').replace(/_/g, '/');
  const completo = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binario = atob(completo);
  const byte = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) {
    byte[i] = binario.charCodeAt(i);
  }
  return byte;
}

function stessiByte(chiave: ArrayBuffer | null, attesa: Uint8Array): boolean {
  if (!chiave) {
    return false;
  }
  const byte = new Uint8Array(chiave);
  return byte.length === attesa.length && byte.every((valore, i) => valore === attesa[i]);
}
