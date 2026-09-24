import { InjectionToken } from '@angular/core';

/**
 * Quello che il browser lascia sapere di sé senza chiedere niente: le API
 * che ci sono, i permessi già dati, lo user agent. Tutto dietro metodi, così
 * ogni lettura è fresca (un permesso si cambia dalle impostazioni a pagina
 * aperta) e i test simulano un iPhone o il browser di WhatsApp senza
 * toccare navigator.
 */
export interface SondeBrowser {
  agente(): string;
  /** navigator.maxTouchPoints: un "Mac" con il touch è un iPad. */
  puntiTocco(): number;
  /** Aperta dalla schermata Home (display-mode standalone o navigator.standalone). */
  installata(): boolean;
  /** HTTPS o localhost: fuori, notifiche e posizione non ci sono. */
  sicura(): boolean;
  /** L'app Capacitor. */
  nativa(): boolean;
  conServiceWorker(): boolean;
  conPushManager(): boolean;
  /** Notification.permission, o null se Notification non c'è proprio. */
  permessoNotifiche(): NotificationPermission | null;
  conGeolocalizzazione(): boolean;
  /**
   * La Permissions-Policy del server vieta la posizione a questa pagina: il
   * browser allora la nega senza chiedere, qualunque permesso abbia dato
   * l'utente. Lo dicono solo i browser basati su Chromium
   * (document.permissionsPolicy, prima featurePolicy); gli altri false, e
   * lì lo rivela il messaggio dell'errore (vedi errorePerCodice).
   */
  posizioneVietataDalSito(): boolean;
  /**
   * navigator.permissions.query, o null quando non si può sapere: l'API
   * manca (Safari prima della 16) o rifiuta il nome (Safari con
   * "notifications", Firefox in certi casi).
   */
  interroga(nome: 'geolocation' | 'notifications'): Promise<PermissionState | null>;
}

/** Le sonde vere, sul browser in cui gira l'app. */
export function sondeReali(): SondeBrowser {
  const conFinestra = typeof window !== 'undefined' && typeof navigator !== 'undefined';
  return {
    agente: () => (conFinestra ? navigator.userAgent : ''),
    puntiTocco: () => (conFinestra ? navigator.maxTouchPoints || 0 : 0),
    installata: () =>
      conFinestra &&
      (window.matchMedia?.('(display-mode: standalone)').matches === true ||
        (navigator as { standalone?: boolean }).standalone === true),
    sicura: () => !conFinestra || window.isSecureContext !== false,
    nativa: () =>
      conFinestra &&
      (window as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor?.isNativePlatform?.() ===
        true,
    conServiceWorker: () => conFinestra && 'serviceWorker' in navigator,
    conPushManager: () => conFinestra && 'PushManager' in window,
    permessoNotifiche: () => (typeof Notification === 'undefined' ? null : Notification.permission),
    conGeolocalizzazione: () => conFinestra && !!navigator.geolocation,
    posizioneVietataDalSito: () => {
      if (typeof document === 'undefined') {
        return false;
      }
      type Politica = { allowsFeature?: (nome: string) => boolean };
      const documento = document as { permissionsPolicy?: Politica; featurePolicy?: Politica };
      const politica = documento.permissionsPolicy ?? documento.featurePolicy;
      try {
        return politica?.allowsFeature?.('geolocation') === false;
      } catch {
        return false;
      }
    },
    interroga: async (nome) => {
      try {
        if (!conFinestra || !navigator.permissions?.query) {
          return null;
        }
        return (await navigator.permissions.query({ name: nome })).state;
      } catch {
        return null;
      }
    },
  };
}

export const SONDE_BROWSER = new InjectionToken<SondeBrowser>('SONDE_BROWSER', {
  providedIn: 'root',
  factory: sondeReali,
});

/** Dove sta girando l'app, quanto basta per dare le istruzioni giuste. */
export interface Piattaforma {
  sistema: 'ios' | 'android' | 'desktop';
  browser: 'safari' | 'chrome' | 'samsung' | 'firefox' | 'edge' | 'altro';
  /**
   * Il browser interno di un'app (WhatsApp, Telegram, Facebook...) con il suo
   * nome, 'un’altra app' se non si sa quale, null se è un browser vero.
   */
  appInterna: string | null;
  installata: boolean;
}

/** Il nome dell'app nel cui browser interno si è aperto il link. */
const APP_INTERNE: [RegExp, string][] = [
  [/FBAN|FBAV|FB_IAB|FBIOS/, 'Facebook'],
  [/Instagram/, 'Instagram'],
  [/WhatsApp/i, 'WhatsApp'],
  [/Telegram/i, 'Telegram'],
  [/\bLine\//, 'LINE'],
  [/musical_ly|TikTok|BytedanceWebview/i, 'TikTok'],
  [/Snapchat/i, 'Snapchat'],
  [/LinkedInApp/i, 'LinkedIn'],
];

export const APP_GENERICA = 'un’altra app';

export function riconosciPiattaforma(
  agente: string,
  puntiTocco: number,
  installata: boolean,
): Piattaforma {
  // Gli iPad recenti si presentano come un Mac: li tradisce il touch.
  const ios = /iPad|iPhone|iPod/.test(agente) || (/Macintosh/.test(agente) && puntiTocco > 1);
  const android = !ios && /Android/.test(agente);
  const sistema = ios ? 'ios' : android ? 'android' : 'desktop';

  let browser: Piattaforma['browser'] = 'altro';
  if (/SamsungBrowser/.test(agente)) {
    browser = 'samsung';
  } else if (/EdgiOS|EdgA|Edg\//.test(agente)) {
    browser = 'edge';
  } else if (/FxiOS|Firefox\//.test(agente)) {
    browser = 'firefox';
  } else if (/CriOS|Chrome\//.test(agente)) {
    browser = 'chrome';
  } else if (/Safari\//.test(agente) || (ios && installata)) {
    browser = 'safari';
  }

  let appInterna = APP_INTERNE.find(([modello]) => modello.test(agente))?.[1] ?? null;
  if (!appInterna && !installata) {
    // Senza nome: su Android le WebView hanno "; wv)" nello user agent; su
    // iPhone le WebView delle app non dicono "Safari/", che invece c'è in
    // Safari e in tutti i browser veri (Chrome e Firefox compresi). L'app
    // dalla schermata Home non lo dice nemmeno lei, ma lì è installata.
    if ((android && /; wv\)/.test(agente)) || (ios && !/Safari\//.test(agente))) {
      appInterna = APP_GENERICA;
    }
  }
  return { sistema, browser, appInterna, installata };
}
