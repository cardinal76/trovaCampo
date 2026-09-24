import { SondeBrowser } from './sonde-browser';

/** Gli user agent veri dei casi che la diagnosi deve riconoscere. */
export const AGENTI = {
  iphoneSafari:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
  /** L'app aggiunta alla schermata Home non dice "Safari/". */
  iphoneHome:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) Mobile/15E148',
  iphoneChrome:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) CriOS/126.0.6478.54 Mobile/15E148 Safari/604.1',
  iphoneFacebook:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) Mobile/15E148 [FBAN/FBIOS;FBAV/470.0.0.40.108;FBBV/617233036]',
  /** Una WebView di un'app qualunque su iPhone: niente "Safari/". */
  iphoneWebView:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) Mobile/15E148',
  /** Gli iPad recenti si presentano come un Mac. */
  ipad:
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) Version/17.5 Safari/605.1.15',
  androidChrome:
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) ' +
    'Chrome/126.0.0.0 Mobile Safari/537.36',
  androidSamsung:
    'Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) ' +
    'SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36',
  androidTelegram:
    'Mozilla/5.0 (Linux; Android 14; SM-S911B Build/UP1A.231005.007; wv) AppleWebKit/537.36 ' +
    '(KHTML, like Gecko) Version/4.0 Chrome/126.0.6478.71 Mobile Safari/537.36 Telegram-Android/10.14.5',
  androidWhatsApp:
    'Mozilla/5.0 (Linux; Android 13; SM-A536B Build/TP1A; wv) AppleWebKit/537.36 ' +
    '(KHTML, like Gecko) Version/4.0 Chrome/126.0 Mobile Safari/537.36 WhatsApp/2.24.13.78',
  androidInstagram:
    'Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/AP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) ' +
    'Version/4.0 Chrome/126.0 Mobile Safari/537.36 Instagram 339.0.0.30.105 Android',
  androidWebView:
    'Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A; wv) AppleWebKit/537.36 ' +
    '(KHTML, like Gecko) Version/4.0 Chrome/126.0 Mobile Safari/537.36',
  desktopChrome:
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
    'Chrome/126.0.0.0 Safari/537.36',
  desktopFirefox:
    'Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0',
  macSafari:
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 ' +
    '(KHTML, like Gecko) Version/17.5 Safari/605.1.15',
} as const;

/**
 * Il browser simulato per la diagnosi: di partenza un Chrome desktop con
 * tutto, e ogni test cambia solo quello che gli serve. `permessi` a null
 * vuol dire Permissions API assente (o che rifiuta il nome), come su
 * Safari vecchi.
 */
export class SondeFinte implements SondeBrowser {
  userAgent: string = AGENTI.desktopChrome;
  tocco = 0;
  daHome = false;
  sicuro = true;
  capacitor = false;
  serviceWorker = true;
  pushManager = true;
  notifiche: NotificationPermission | null = 'default';
  geolocalizzazione = true;
  permessi: Partial<Record<'geolocation' | 'notifications', PermissionState | null>> | null = {
    geolocation: 'prompt',
    notifications: 'prompt',
  };

  agente = () => this.userAgent;
  puntiTocco = () => this.tocco;
  installata = () => this.daHome;
  sicura = () => this.sicuro;
  nativa = () => this.capacitor;
  conServiceWorker = () => this.serviceWorker;
  conPushManager = () => this.pushManager;
  permessoNotifiche = () => this.notifiche;
  conGeolocalizzazione = () => this.geolocalizzazione;
  interroga = async (nome: 'geolocation' | 'notifications') => this.permessi?.[nome] ?? null;

  /** Un iPhone con Safari, non dalla schermata Home: niente Notification né PushManager. */
  static iphoneSafari(): SondeFinte {
    const sonde = new SondeFinte();
    sonde.userAgent = AGENTI.iphoneSafari;
    sonde.tocco = 5;
    sonde.pushManager = false;
    sonde.notifiche = null;
    return sonde;
  }

  static androidChrome(): SondeFinte {
    const sonde = new SondeFinte();
    sonde.userAgent = AGENTI.androidChrome;
    sonde.tocco = 5;
    return sonde;
  }
}
