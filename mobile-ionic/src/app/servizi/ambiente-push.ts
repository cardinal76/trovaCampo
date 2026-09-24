import { Injectable, inject } from '@angular/core';
import { SONDE_BROWSER, SondeBrowser, riconosciPiattaforma } from './sonde-browser';

/**
 * Cosa sa fare questo browser con le notifiche push:
 *
 * - `supportato`: si può accendere tutto;
 * - `iphone-da-installare`: iPhone o iPad nel browser. Safari dà le notifiche
 *   solo al sito aggiunto alla schermata Home (iOS 16.4 e successivi) e
 *   aperto da lì;
 * - `browser-in-app`: il browser interno di un'app (WhatsApp, Telegram,
 *   Facebook...), dove le push non arrivano: va aperto in Chrome o Safari;
 * - `app-nativa`: l'app Capacitor, dove le push del web non ci sono;
 * - `non-sicuro`: pagina non in HTTPS, dove il browser non le permette;
 * - `non-supportato`: un browser che non le ha (o un iPhone con iOS vecchio).
 */
export type SupportoPush =
  | 'supportato'
  | 'iphone-da-installare'
  | 'browser-in-app'
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
  private readonly sonde = inject(SONDE_BROWSER);

  supporto(): SupportoPush {
    return supportoPush(this.sonde);
  }

  permesso(): NotificationPermission {
    return this.sonde.permessoNotifiche() ?? 'denied';
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
    // Safari vecchi non hanno navigator.permissions: null, cioè non si sa.
    return (await this.sonde.interroga('geolocation')) ?? 'sconosciuto';
  }
}

/** Cosa sa fare con le push il browser descritto dalle sonde. */
export function supportoPush(sonde: SondeBrowser): SupportoPush {
  if (sonde.nativa()) {
    return 'app-nativa';
  }
  if (!sonde.sicura()) {
    return 'non-sicuro';
  }
  const conPush =
    sonde.conServiceWorker() && sonde.conPushManager() && sonde.permessoNotifiche() !== null;
  const piattaforma = riconosciPiattaforma(sonde.agente(), sonde.puntiTocco(), sonde.installata());
  // Prima il browser interno: su iPhone "aggiungi alla Home" non si può
  // fare da WhatsApp, bisogna prima aprire il sito in Safari.
  if (piattaforma.appInterna && (piattaforma.sistema === 'ios' || !conPush)) {
    return 'browser-in-app';
  }
  // Su iPhone il PushManager compare solo nel sito aperto dalla Home: nel
  // Safari normale manca anche con iOS recente, e la cosa da dire è
  // "aggiungilo alla Home", non "il tuo browser non va".
  if (piattaforma.sistema === 'ios' && !piattaforma.installata) {
    return 'iphone-da-installare';
  }
  return conPush ? 'supportato' : 'non-supportato';
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
