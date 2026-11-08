import { Istruzioni, browserVero, nomeApp, nomeBrowser, passiAppInterna } from './comune';
import { Piattaforma } from '../sonde-browser';
import { CasoNotifiche } from '../diagnosi.service';

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
        spiegazione: 'Il browser permette notifiche e posizione solo alle pagine aperte in HTTPS.',
        passi: ['Apri https://trovacampo.footballer.it (con https://) e torna su Notifiche.'],
        riprova: false,
      };

    case 'app-nativa':
      return {
        caso,
        icona: 'alert-circle-outline',
        titolo: 'Nell’app le notifiche non ci sono ancora',
        spiegazione: 'Apri trovacampo.footballer.it dal browser del telefono e accendile da lì.',
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
