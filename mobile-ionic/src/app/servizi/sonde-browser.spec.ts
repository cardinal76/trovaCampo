import { supportoPush } from './ambiente-push';
import { AGENTI, SondeFinte } from './sonde-finte.spec';
import { APP_GENERICA, riconosciPiattaforma, sondeReali } from './sonde-browser';

describe('riconosciPiattaforma', () => {
  it('iPhone con Safari', () => {
    expect(riconosciPiattaforma(AGENTI.iphoneSafari, 5, false)).toEqual({
      sistema: 'ios',
      browser: 'safari',
      appInterna: null,
      installata: false,
    });
  });

  it('l app dalla schermata Home è Safari installata, non un browser interno', () => {
    expect(riconosciPiattaforma(AGENTI.iphoneHome, 5, true)).toEqual({
      sistema: 'ios',
      browser: 'safari',
      appInterna: null,
      installata: true,
    });
  });

  it('un iPad che si presenta come Mac lo tradisce il touch', () => {
    expect(riconosciPiattaforma(AGENTI.ipad, 5, false).sistema).toBe('ios');
    expect(riconosciPiattaforma(AGENTI.macSafari, 0, false).sistema).toBe('desktop');
  });

  it('Chrome su iPhone e su Android, Samsung Internet, Firefox sul computer', () => {
    expect(riconosciPiattaforma(AGENTI.iphoneChrome, 5, false)).toEqual(
      jasmine.objectContaining({ sistema: 'ios', browser: 'chrome', appInterna: null }),
    );
    expect(riconosciPiattaforma(AGENTI.androidChrome, 5, false)).toEqual(
      jasmine.objectContaining({ sistema: 'android', browser: 'chrome', appInterna: null }),
    );
    expect(riconosciPiattaforma(AGENTI.androidSamsung, 5, false).browser).toBe('samsung');
    expect(riconosciPiattaforma(AGENTI.desktopFirefox, 0, false)).toEqual(
      jasmine.objectContaining({ sistema: 'desktop', browser: 'firefox' }),
    );
  });

  for (const [agente, app] of [
    [AGENTI.iphoneFacebook, 'Facebook'],
    [AGENTI.androidTelegram, 'Telegram'],
    [AGENTI.androidWhatsApp, 'WhatsApp'],
    [AGENTI.androidInstagram, 'Instagram'],
    [AGENTI.androidWebView, APP_GENERICA],
    [AGENTI.iphoneWebView, APP_GENERICA],
  ] as const) {
    it(`riconosce il browser interno di ${app}`, () => {
      expect(riconosciPiattaforma(agente, 5, false).appInterna).toBe(app);
    });
  }
});

describe('supportoPush', () => {
  it('Chrome con tutto: supportato', () => {
    expect(supportoPush(new SondeFinte())).toBe('supportato');
  });

  it('iPhone con Safari fuori dalla Home: da installare', () => {
    expect(supportoPush(SondeFinte.iphoneSafari())).toBe('iphone-da-installare');
  });

  it('iPhone dalla Home con iOS recente: supportato', () => {
    const sonde = SondeFinte.iphoneSafari();
    sonde.userAgent = AGENTI.iphoneHome;
    sonde.daHome = true;
    sonde.pushManager = true;
    sonde.notifiche = 'default';
    expect(supportoPush(sonde)).toBe('supportato');
  });

  it('il browser interno di un app viene prima di "aggiungi alla Home"', () => {
    const sonde = SondeFinte.iphoneSafari();
    sonde.userAgent = AGENTI.iphoneFacebook;
    expect(supportoPush(sonde)).toBe('browser-in-app');
  });

  it('WebView Android senza push: browser in app', () => {
    const sonde = SondeFinte.androidChrome();
    sonde.userAgent = AGENTI.androidTelegram;
    sonde.pushManager = false;
    expect(supportoPush(sonde)).toBe('browser-in-app');
  });

  it('senza service worker (Firefox in privato): non supportato', () => {
    const sonde = new SondeFinte();
    sonde.userAgent = AGENTI.desktopFirefox;
    sonde.serviceWorker = false;
    expect(supportoPush(sonde)).toBe('non-supportato');
  });

  it('non in HTTPS, e nell app nativa', () => {
    const insicuro = new SondeFinte();
    insicuro.sicuro = false;
    expect(supportoPush(insicuro)).toBe('non-sicuro');
    const nativa = new SondeFinte();
    nativa.capacitor = true;
    expect(supportoPush(nativa)).toBe('app-nativa');
  });
});

describe('sondeReali', () => {
  it('senza Permissions API risponde null invece di fallire', async () => {
    spyOnProperty(navigator, 'permissions').and.returnValue(undefined as never);
    expect(await sondeReali().interroga('geolocation')).toBeNull();
  });

  it('se la Permissions API rifiuta il nome risponde null', async () => {
    spyOn(navigator.permissions, 'query').and.rejectWith(new TypeError('nome sconosciuto'));
    expect(await sondeReali().interroga('notifications')).toBeNull();
  });

  it('legge lo stato del permesso quando c è', async () => {
    spyOn(navigator.permissions, 'query').and.resolveTo({ state: 'denied' } as PermissionStatus);
    expect(await sondeReali().interroga('geolocation')).toBe('denied');
  });
});
