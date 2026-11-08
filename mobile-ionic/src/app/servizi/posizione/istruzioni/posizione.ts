import { Istruzioni, browserVero, nomeApp, nomeBrowser, passiAppInterna } from './comune';
import { Piattaforma } from '../sonde-browser';
import { CasoPosizione } from '../diagnosi.service';

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
    case 'vietata-dal-sito':
      // Non c'è niente da sbloccare sul telefono: mandare l'utente nelle
      // impostazioni vorrebbe dire fargli cercare un problema che non ha.
      return {
        caso,
        icona: 'construct-outline',
        titolo: 'La posizione è disattivata dal sito',
        spiegazione:
          'Non dipende dal tuo telefono né dal browser: è il sito che per ora impedisce di ' +
          'usarla, e i permessi che dai non cambiano niente.',
        passi: [
          'Non serve toccare le impostazioni del telefono o del browser.',
          'Segnalalo a chi gestisce TrovaCampo; quando è corretto, ricarica la pagina e tocca Riprova.',
          'Intanto puoi cercare i campi per nome o scegliere la provincia.',
        ],
        riprova: true,
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
