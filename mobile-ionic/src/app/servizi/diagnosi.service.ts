import { Injectable, computed, inject, signal } from '@angular/core';
import { AmbientePush, SupportoPush } from './ambiente-push';
import { ErrorePosizione } from './posizione.service';
import { SONDE_BROWSER, riconosciPiattaforma } from './sonde-browser';

/**
 * Perché le notifiche arrivano o no su questo dispositivo:
 *
 * - `consentite`, `da-consentire`: tutto a posto, o manca solo il "Consenti";
 * - `bloccate`: il sito le ha negate (il browser non le chiede più);
 * - `iphone-da-installare`: iPhone/iPad nel browser, serve la schermata Home;
 * - `browser-in-app`: il browser interno di WhatsApp, Telegram, Facebook...;
 * - `non-supportate`: niente push qui (navigazione privata, browser senza
 *   push, o l'iscrizione al servizio push del browser è fallita);
 * - `non-sicuro`, `app-nativa`: pagina non in HTTPS, app Capacitor.
 */
export type CasoNotifiche =
  | 'consentite'
  | 'da-consentire'
  | 'bloccate'
  | 'iphone-da-installare'
  | 'browser-in-app'
  | 'non-supportate'
  | 'non-sicuro'
  | 'app-nativa';

/**
 * Perché la posizione arriva o no:
 *
 * - `consentita`, `da-consentire`: permesso dato, o non ancora chiesto;
 * - `rifiutata`: l'ultima richiesta è finita in un no (o è stata chiusa), ma
 *   il browser la può rifare;
 * - `bloccata`: negata per il sito, il browser non la chiede più;
 * - `vietata-dal-sito`: la vieta la Permissions-Policy mandata dal server;
 *   il browser la nega senza chiedere e i permessi dell'utente non contano;
 * - `non-disponibile`: permesso dato, ma il telefono non sa dove si trova
 *   (localizzazione spenta, nessun segnale);
 * - `scaduta`: la posizione non è arrivata in tempo;
 * - `non-supportata`, `non-sicura`: niente geolocalizzazione, o non in HTTPS;
 * - `sconosciuta`: le API non lo dicono e non la si è ancora chiesta.
 */
export type CasoPosizione =
  | 'consentita'
  | 'da-consentire'
  | 'rifiutata'
  | 'bloccata'
  | 'vietata-dal-sito'
  | 'non-disponibile'
  | 'scaduta'
  | 'non-supportata'
  | 'non-sicura'
  | 'sconosciuta';

/** Com'è finita l'ultima lettura della posizione: 'ok', il motivo dell'errore, o mai provata. */
type UltimaLettura = ErrorePosizione['motivo'] | 'ok' | null;

export function casoNotifiche(
  supporto: SupportoPush,
  permesso: NotificationPermission,
  iscrizioneFallita: boolean,
): CasoNotifiche {
  switch (supporto) {
    case 'supportato':
      if (permesso === 'denied') {
        // In navigazione privata Chrome le dà per negate senza chiedere: le
        // istruzioni dei permessi bloccati lo ricordano.
        return 'bloccate';
      }
      if (iscrizioneFallita) {
        return 'non-supportate';
      }
      return permesso === 'granted' ? 'consentite' : 'da-consentire';
    case 'non-supportato':
      return 'non-supportate';
    default:
      return supporto;
  }
}

/**
 * Mette insieme lo stato del permesso (se le API lo danno) e com'è andata
 * l'ultima richiesta vera. Il permesso da solo non basta: con la
 * localizzazione del telefono spenta dice "granted" e la posizione non
 * arriva; e su iPhone, con la posizione negata a Safari dalle impostazioni,
 * può dire "prompt" mentre la richiesta fallisce subito senza chiedere niente.
 */
export function casoPosizione(
  sicura: boolean,
  conGeolocalizzazione: boolean,
  stato: PermissionState | null,
  ultima: UltimaLettura,
  vietataDalSito = false,
): CasoPosizione {
  if (!sicura || ultima === 'non-sicura') {
    return 'non-sicura';
  }
  // Prima di tutto il resto: con la posizione vietata dal server Chrome dice
  // anche "denied", e senza questo controllo sembrerebbe un blocco del
  // browser, da sbloccare in impostazioni dove invece è tutto a posto.
  if (vietataDalSito || ultima === 'vietata') {
    return 'vietata-dal-sito';
  }
  if (!conGeolocalizzazione || ultima === 'non-supportata') {
    return 'non-supportata';
  }
  if (stato === 'denied') {
    return 'bloccata';
  }
  if (ultima === 'negata') {
    // Negata all'ultima richiesta: se ora il permesso c'è, l'hanno appena
    // sbloccata; se il browser dice "prompt" la può richiedere (un no, o la
    // richiesta chiusa); senza API non si sa, e si danno le istruzioni per
    // sbloccarla, che valgono anche nel caso migliore.
    return stato === 'granted' ? 'consentita' : stato === 'prompt' ? 'rifiutata' : 'bloccata';
  }
  if (ultima === 'non-disponibile' || ultima === 'scaduta') {
    return ultima;
  }
  if (stato === 'granted' || ultima === 'ok') {
    return 'consentita';
  }
  return stato === 'prompt' ? 'da-consentire' : 'sconosciuta';
}

/**
 * Cosa blocca notifiche e posizione su questo dispositivo, perché le pagine
 * possano dirlo con le parole giuste e spiegare come sbloccarlo.
 *
 * Non chiede mai permessi: legge quello che il browser dice (Notification,
 * Permissions API, user agent) e ricorda com'è andata l'ultima richiesta
 * vera, che la fanno NotificheService e la Mappa e qui la raccontano.
 */
@Injectable({ providedIn: 'root' })
export class DiagnosiService {
  private readonly sonde = inject(SONDE_BROWSER);
  private readonly ambiente = inject(AmbientePush);

  readonly piattaforma = riconosciPiattaforma(
    this.sonde.agente(),
    this.sonde.puntiTocco(),
    this.sonde.installata(),
  );

  private readonly supporto = signal<SupportoPush>(this.ambiente.supporto());
  private readonly permessoNotifiche = signal<NotificationPermission>(this.ambiente.permesso());
  private readonly iscrizioneFallita = signal(false);
  private readonly statoPosizione = signal<PermissionState | null>(null);
  private readonly ultimaLettura = signal<UltimaLettura>(null);

  readonly notifiche = computed(() =>
    casoNotifiche(this.supporto(), this.permessoNotifiche(), this.iscrizioneFallita()),
  );
  readonly posizione = computed(() =>
    casoPosizione(
      this.sonde.sicura(),
      this.sonde.conGeolocalizzazione(),
      this.statoPosizione(),
      this.ultimaLettura(),
      this.sonde.posizioneVietataDalSito(),
    ),
  );

  /** Rilegge tutto: all'ingresso nella pagina, dopo ogni richiesta e con "Riprova". */
  async aggiorna(): Promise<void> {
    this.aggiornaNotifiche();
    await this.aggiornaPosizione();
  }

  /** Il permesso delle notifiche si legge subito, senza attese. */
  aggiornaNotifiche(): void {
    this.supporto.set(this.ambiente.supporto());
    const permesso = this.ambiente.permesso();
    if (permesso !== this.permessoNotifiche()) {
      // Il permesso è cambiato: un'iscrizione fallita prima non conta più.
      this.iscrizioneFallita.set(false);
    }
    this.permessoNotifiche.set(permesso);
  }

  async aggiornaPosizione(): Promise<void> {
    this.statoPosizione.set(await this.sonde.interroga('geolocation'));
  }

  /** Il browser non è riuscito a iscriversi al suo servizio push. */
  segnalaIscrizioneFallita(): void {
    this.iscrizioneFallita.set(true);
  }

  /** L'iscrizione è andata: se prima era fallita, ora non più. */
  segnalaIscrizioneRiuscita(): void {
    this.iscrizioneFallita.set(false);
  }

  /**
   * Com'è andata una richiesta della posizione: null se è arrivata,
   * altrimenti l'errore. Rilegge anche il permesso, che dopo un no dice se
   * il browser la richiederà o no.
   */
  async esitoPosizione(errore: unknown): Promise<void> {
    this.ultimaLettura.set(
      errore === null ? 'ok' : errore instanceof ErrorePosizione ? errore.motivo : 'non-disponibile',
    );
    await this.aggiornaPosizione();
  }
}
