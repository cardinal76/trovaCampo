import { CasoNotifiche, CasoPosizione } from './diagnosi.service';
import { APP_GENERICA, Piattaforma } from './sonde-browser';

/**
 * Un riquadro "cosa blocca e come sbloccarlo": titolo, due righe di
 * spiegazione e i passi, pensati per il dispositivo che si ha in mano.
 */
export interface Istruzioni {
  /** Il caso, per i test e per lo stile (data-caso). */
  caso: string;
  icona: string;
  titolo: string;
  spiegazione: string;
  passi: string[];
  /** Una riga dopo i passi, per il caso meno probabile. */
  nota?: string;
  /** "Riprova" serve solo dove qualcosa si può sbloccare da qui. */
  riprova: boolean;
}

/** Il nome del browser nei passi: "Impostazioni → App → Chrome". */
function nomeBrowser(piattaforma: Piattaforma): string {
  switch (piattaforma.browser) {
    case 'chrome':
      return 'Chrome';
    case 'samsung':
      return 'Samsung Internet';
    case 'firefox':
      return 'Firefox';
    case 'edge':
      return 'Edge';
    case 'safari':
      return 'Safari';
    default:
      return 'il browser';
  }
}

/** Il browser vero in cui riaprire il sito, uscendo da quello di un'app. */
function browserVero(piattaforma: Piattaforma): string {
  return piattaforma.sistema === 'ios' ? 'Safari' : 'Chrome';
}

/** Come uscire dal browser interno di un'app: vale per notifiche e posizione. */
function passiAppInterna(piattaforma: Piattaforma): string[] {
  const browser = browserVero(piattaforma);
  return [
    piattaforma.sistema === 'ios'
      ? 'Tocca i tre puntini (…) o l’icona Condividi, in alto o in basso nella pagina.'
      : 'Tocca i tre puntini (⋮) in alto a destra.',
    `Scegli «Apri in ${browser}» (o «Apri nel browser»).`,
    piattaforma.sistema === 'ios'
      ? 'In Safari tocca Condividi → «Aggiungi alla schermata Home», e apri TrovaCampo da lì.'
      : `In ${browser} apri il menu → Notifiche, e accendi l’avviso da lì.`,
  ];
}

function nomeApp(piattaforma: Piattaforma): string {
  return piattaforma.appInterna === APP_GENERICA
    ? 'un’altra app'
    : (piattaforma.appInterna ?? 'un’altra app');
}

/**
 * Le istruzioni per le notifiche, o null se non c'è niente da sbloccare
 * (consentite, o da consentire con il prossimo tocco).
 */
export function istruzioniNotifiche(
  caso: CasoNotifiche,
  piattaforma: Piattaforma,
): Istruzioni | null {
  const { sistema } = piattaforma;
  const browser = nomeBrowser(piattaforma);
  switch (caso) {
    case 'consentite':
    case 'da-consentire':
      return null;

    case 'iphone-da-installare':
      return {
        caso,
        icona: 'share-outline',
        titolo: 'Su iPhone e iPad le notifiche arrivano solo dalla schermata Home',
        spiegazione:
          'Apple le dà ai siti solo se li aggiungi alla schermata Home e li apri da lì ' +
          '(iOS 16.4 o successivo). La posizione invece puoi darla anche da qui.',
        passi: [
          piattaforma.browser === 'safari' || piattaforma.browser === 'altro'
            ? 'Tocca Condividi (il quadrato con la freccia in su), in basso o accanto all’indirizzo.'
            : `In ${browser} tocca Condividi (il quadrato con la freccia in su), accanto all’indirizzo.`,
          'Scorri e scegli «Aggiungi alla schermata Home», poi «Aggiungi».',
          'Chiudi il browser e apri TrovaCampo dall’icona sulla schermata Home.',
          'Vai su Notifiche e accendi l’avviso: ti chiederà il permesso.',
        ],
        nota: 'Se «Aggiungi alla schermata Home» non c’è, aggiorna iOS da Impostazioni → Generali → Aggiornamento software.',
        riprova: false,
      };

    case 'browser-in-app':
      return {
        caso,
        icona: 'open-outline',
        titolo: `Sei nel browser interno di ${nomeApp(piattaforma)}`,
        spiegazione:
          `Il browser dentro le app non riceve notifiche e spesso non dà la posizione. ` +
          `Apri la pagina in ${browserVero(piattaforma)}.`,
        passi: passiAppInterna(piattaforma),
        riprova: false,
      };

    case 'non-supportate':
      return {
        caso,
        icona: 'eye-off-outline',
        titolo: 'Qui le notifiche push non funzionano',
        spiegazione:
          'Succede in navigazione privata (in incognito), con browser che non le hanno, o se ' +
          'il servizio push del browser non risponde.',
        passi:
          sistema === 'android'
            ? [
                'Apri trovacampo.footballer.it in una scheda normale di Chrome, non in incognito.',
                'Vai su Notifiche e accendi l’avviso.',
              ]
            : [
                'Apri trovacampo.footballer.it in una finestra normale (non privata) di Chrome, ' +
                  'Edge, Firefox o Safari aggiornati.',
                'Vai su Notifiche e accendi l’avviso.',
              ],
        nota: 'Se sei già in una finestra normale, tocca Riprova tra qualche minuto.',
        riprova: true,
      };

    case 'bloccate':
      return {
        caso,
        icona: 'notifications-off-outline',
        titolo: 'Le notifiche sono bloccate per questo sito',
        spiegazione:
          'Il browser non le chiede più: vanno riattivate dalle impostazioni, poi tocca Riprova.',
        passi: passiNotificheBloccate(piattaforma),
        nota: 'In navigazione privata sono sempre bloccate: apri il sito in una finestra normale.',
        riprova: true,
      };

    case 'non-sicuro':
      return {
        caso,
        icona: 'lock-open-outline',
        titolo: 'Pagina non sicura',
        spiegazione:
          'Il browser permette notifiche e posizione solo alle pagine aperte in HTTPS.',
        passi: ['Apri https://trovacampo.footballer.it (con https://) e torna su Notifiche.'],
        riprova: false,
      };

    case 'app-nativa':
      return {
        caso,
        icona: 'alert-circle-outline',
        titolo: 'Nell’app le notifiche non ci sono ancora',
        spiegazione:
          'Apri trovacampo.footballer.it dal browser del telefono e accendile da lì.',
        passi: [],
        riprova: false,
      };
  }
}

function passiNotificheBloccate(piattaforma: Piattaforma): string[] {
  const browser = nomeBrowser(piattaforma);
  switch (piattaforma.sistema) {
    case 'android':
      return [
        'Tocca l’icona a sinistra dell’indirizzo (le levette o il lucchetto).',
        'Autorizzazioni → Notifiche → Consenti (o «Reimposta autorizzazioni»).',
        `Se non basta: Impostazioni del telefono → App → ${browser} → Notifiche → attivale.`,
        'Torna qui e tocca Riprova.',
      ];
    case 'ios':
      return [
        'Apri Impostazioni → Notifiche → TrovaCampo.',
        'Attiva «Consenti notifiche».',
        'Torna nell’app e tocca Riprova.',
      ];
    default:
      return [
        'Fai clic sull’icona a sinistra dell’indirizzo (il lucchetto o le levette).',
        'Notifiche → Consenti (o «Reimposta autorizzazioni»).',
        'Torna qui e tocca Riprova (se non basta, ricarica la pagina).',
      ];
  }
}

/**
 * Le istruzioni per la posizione, o null se non c'è niente da sbloccare
 * (consentita, da consentire, o non si sa ancora).
 */
export function istruzioniPosizione(
  caso: CasoPosizione,
  piattaforma: Piattaforma,
): Istruzioni | null {
  switch (caso) {
    case 'consentita':
    case 'da-consentire':
    case 'sconosciuta':
      return null;
    case 'non-sicura':
      return {
        caso,
        icona: 'lock-open-outline',
        titolo: 'Pagina non sicura',
        spiegazione: 'Il browser dà la posizione solo alle pagine aperte in HTTPS.',
        passi: ['Apri https://trovacampo.footballer.it (con https://) e riprova.'],
        riprova: false,
      };
    case 'non-supportata':
      return {
        caso,
        icona: 'alert-circle-outline',
        titolo: 'Questo browser non sa dare la posizione',
        spiegazione: 'Prova con Chrome, Safari, Firefox o Edge aggiornati, o dal telefono.',
        passi: [],
        riprova: false,
      };
  }

  // Nel browser di un'app i permessi dipendono dall'app, e spesso la
  // posizione non c'è proprio: la cosa giusta è uscirne.
  if (piattaforma.appInterna) {
    return {
      caso,
      icona: 'open-outline',
      titolo: `Sei nel browser interno di ${nomeApp(piattaforma)}`,
      spiegazione:
        `Qui la posizione spesso non arriva. Apri la pagina in ${browserVero(piattaforma)} ` +
        'e riprova da lì.',
      passi: passiAppInterna(piattaforma).slice(0, 2),
      riprova: true,
    };
  }

  switch (caso) {
    case 'rifiutata':
      return {
        caso,
        icona: 'location-outline',
        titolo: 'Non hai dato il permesso della posizione',
        spiegazione:
          'Tocca Riprova e, quando il browser lo chiede, scegli «Consenti». ' +
          'Se la richiesta non compare:',
        passi: passiPosizioneBloccata(piattaforma),
        riprova: true,
      };
    case 'bloccata':
      return {
        caso,
        icona: 'location-outline',
        titolo: 'La posizione è bloccata per questo sito',
        spiegazione:
          'Il browser non la chiede più: va sbloccata dalle impostazioni, poi tocca Riprova.',
        passi: passiPosizioneBloccata(piattaforma),
        riprova: true,
      };
    case 'non-disponibile':
      return {
        caso,
        icona: 'navigate-outline',
        titolo: 'Il telefono non sa dove ti trovi',
        spiegazione:
          'Il permesso c’è, ma la posizione non arriva: di solito è la localizzazione spenta.',
        passi: passiLocalizzazioneSpenta(piattaforma),
        riprova: true,
      };
    case 'scaduta':
      return {
        caso,
        icona: 'time-outline',
        titolo: 'La posizione non è arrivata in tempo',
        spiegazione: 'Il segnale è debole, o il GPS ci sta mettendo troppo.',
        passi: [
          piattaforma.sistema === 'desktop'
            ? 'Accendi il Wi-Fi: il computer lo usa per capire dove sei.'
            : 'Controlla che la localizzazione del telefono sia accesa, e accendi il Wi-Fi: aiuta anche al chiuso.',
          'Spostati vicino a una finestra o all’aperto.',
          'Tocca Riprova.',
        ],
        riprova: true,
      };
  }
}

function passiPosizioneBloccata(piattaforma: Piattaforma): string[] {
  const browser = nomeBrowser(piattaforma);
  switch (piattaforma.sistema) {
    case 'android':
      return [
        'Tocca l’icona a sinistra dell’indirizzo → Autorizzazioni → Posizione → Consenti ' +
          '(o «Reimposta autorizzazioni»).',
        `Impostazioni del telefono → App → ${browser} → Autorizzazioni → Posizione → ` +
          '«Consenti solo mentre l’app è in uso».',
        'Accendi la localizzazione: tendina in alto → Posizione.',
        'Torna qui e tocca Riprova (se non basta, ricarica la pagina).',
      ];
    case 'ios':
      return piattaforma.browser === 'safari' || piattaforma.browser === 'altro'
        ? [
            'Impostazioni → Privacy e sicurezza → Localizzazione: attivala, poi «Siti web di ' +
              'Safari» → «Mentre usi l’app».',
            'Impostazioni → App → Safari → Posizione → «Chiedi» (o «Consenti»). ' +
              'Su iOS più vecchi è Impostazioni → Safari → Posizione.',
            'Torna qui e tocca Riprova (se non basta, ricarica la pagina).',
          ]
        : [
            `Impostazioni → Privacy e sicurezza → Localizzazione → ${browser} → «Mentre usi l’app».`,
            `In ${browser}: tocca l’icona accanto all’indirizzo e consenti la posizione al sito.`,
            'Torna qui e tocca Riprova (se non basta, ricarica la pagina).',
          ];
    default:
      return [
        'Fai clic sull’icona a sinistra dell’indirizzo → Posizione → Consenti ' +
          '(o «Reimposta autorizzazioni»).',
        `Sul Mac: Impostazioni di Sistema → Privacy e sicurezza → Localizzazione → ${browser} ` +
          'attivo. Su Windows: Impostazioni → Privacy e sicurezza → Posizione attiva.',
        'Torna qui e tocca Riprova (se non basta, ricarica la pagina).',
      ];
  }
}

function passiLocalizzazioneSpenta(piattaforma: Piattaforma): string[] {
  const browser = nomeBrowser(piattaforma);
  switch (piattaforma.sistema) {
    case 'android':
      return [
        'Scorri la tendina dall’alto e accendi «Posizione» (Localizzazione).',
        `Impostazioni → App → ${browser} → Autorizzazioni → Posizione → ` +
          '«Consenti solo mentre l’app è in uso».',
        'Tocca Riprova, meglio con il Wi-Fi acceso o all’aperto.',
      ];
    case 'ios':
      return [
        'Impostazioni → Privacy e sicurezza → Localizzazione: attivala.',
        piattaforma.browser === 'safari' || piattaforma.browser === 'altro'
          ? 'Nella stessa pagina: «Siti web di Safari» → «Mentre usi l’app».'
          : `Nella stessa pagina: ${browser} → «Mentre usi l’app».`,
        'Tocca Riprova, meglio con il Wi-Fi acceso o all’aperto.',
      ];
    default:
      return [
        'Accendi il Wi-Fi: il computer lo usa per capire dove sei.',
        `Sul Mac: Impostazioni di Sistema → Privacy e sicurezza → Localizzazione → ${browser} ` +
          'attivo. Su Windows: Impostazioni → Privacy e sicurezza → Posizione attiva.',
        'Tocca Riprova.',
      ];
  }
}

/** Le due parole della riga di stato in cima alla pagina Notifiche. */
export function statoNotifiche(caso: CasoNotifiche): { testo: string; tono: Tono } {
  switch (caso) {
    case 'consentite':
      return { testo: 'consentite', tono: 'bene' };
    case 'da-consentire':
      return { testo: 'da consentire', tono: 'neutro' };
    case 'bloccate':
      return { testo: 'bloccate', tono: 'male' };
    default:
      return { testo: 'non disponibili qui', tono: 'attenzione' };
  }
}

/** null quando le API non lo dicono e non la si è ancora chiesta: meglio tacere che tirare a indovinare. */
export function statoPosizione(caso: CasoPosizione): { testo: string; tono: Tono } | null {
  switch (caso) {
    case 'consentita':
      return { testo: 'consentita', tono: 'bene' };
    case 'da-consentire':
      return { testo: 'da consentire', tono: 'neutro' };
    case 'rifiutata':
      return { testo: 'non consentita', tono: 'attenzione' };
    case 'bloccata':
      return { testo: 'bloccata', tono: 'male' };
    case 'non-disponibile':
    case 'scaduta':
      return { testo: 'non trovata', tono: 'attenzione' };
    case 'non-supportata':
    case 'non-sicura':
      return { testo: 'non disponibile qui', tono: 'attenzione' };
    case 'sconosciuta':
      return null;
  }
}

export type Tono = 'bene' | 'neutro' | 'attenzione' | 'male';
