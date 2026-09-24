import { CasoNotifiche, CasoPosizione } from './diagnosi.service';
import {
  istruzioniNotifiche,
  istruzioniPosizione,
  statoNotifiche,
  statoPosizione,
} from './istruzioni';
import { APP_GENERICA, Piattaforma, riconosciPiattaforma } from './sonde-browser';
import { AGENTI } from './sonde-finte.spec';

const ANDROID = riconosciPiattaforma(AGENTI.androidChrome, 5, false);
const SAMSUNG = riconosciPiattaforma(AGENTI.androidSamsung, 5, false);
const IPHONE = riconosciPiattaforma(AGENTI.iphoneSafari, 5, false);
const IPHONE_HOME = riconosciPiattaforma(AGENTI.iphoneHome, 5, true);
const IPHONE_CHROME = riconosciPiattaforma(AGENTI.iphoneChrome, 5, false);
const COMPUTER = riconosciPiattaforma(AGENTI.desktopChrome, 0, false);

/** Tutto il testo del riquadro, per cercarci dentro. */
function testo(piattaforma: Piattaforma, caso: CasoPosizione | CasoNotifiche, di: 'n' | 'p'): string {
  const istruzioni =
    di === 'n'
      ? istruzioniNotifiche(caso as CasoNotifiche, piattaforma)
      : istruzioniPosizione(caso as CasoPosizione, piattaforma);
  expect(istruzioni).withContext(`${caso} su ${piattaforma.sistema}`).not.toBeNull();
  return [istruzioni!.titolo, istruzioni!.spiegazione, ...istruzioni!.passi, istruzioni!.nota].join(
    '\n',
  );
}

describe('istruzioni', () => {
  it('niente riquadro quando non c è niente da sbloccare', () => {
    expect(istruzioniNotifiche('consentite', ANDROID)).toBeNull();
    expect(istruzioniNotifiche('da-consentire', ANDROID)).toBeNull();
    expect(istruzioniPosizione('consentita', ANDROID)).toBeNull();
    expect(istruzioniPosizione('da-consentire', ANDROID)).toBeNull();
    expect(istruzioniPosizione('sconosciuta', ANDROID)).toBeNull();
  });

  describe('notifiche', () => {
    it('iPhone non dalla Home: Condividi → Aggiungi alla schermata Home, senza Riprova', () => {
      const istruzioni = istruzioniNotifiche('iphone-da-installare', IPHONE)!;
      expect(istruzioni.passi.join(' ')).toContain('Condividi');
      expect(istruzioni.passi.join(' ')).toContain('Aggiungi alla schermata Home');
      expect(istruzioni.riprova).toBeFalse();
    });

    it('browser interno: aprire in Chrome su Android, in Safari su iPhone', () => {
      const android = { ...ANDROID, appInterna: 'WhatsApp' };
      const iphone = { ...IPHONE, appInterna: 'Facebook' };
      expect(testo(android, 'browser-in-app', 'n')).toContain('WhatsApp');
      expect(testo(android, 'browser-in-app', 'n')).toContain('Apri in Chrome');
      expect(testo(iphone, 'browser-in-app', 'n')).toContain('Apri in Safari');
      expect(testo({ ...ANDROID, appInterna: APP_GENERICA }, 'browser-in-app', 'n')).toContain(
        'un’altra app',
      );
    });

    it('non supportate: parla di navigazione privata', () => {
      expect(testo(ANDROID, 'non-supportate', 'n')).toContain('incognito');
      expect(istruzioniNotifiche('non-supportate', ANDROID)!.riprova).toBeTrue();
    });

    it('bloccate: i passi del telefono che si ha in mano', () => {
      expect(testo(ANDROID, 'bloccate', 'n')).toContain('Autorizzazioni → Notifiche → Consenti');
      expect(testo(ANDROID, 'bloccate', 'n')).toContain('App → Chrome → Notifiche');
      expect(testo(SAMSUNG, 'bloccate', 'n')).toContain('App → Samsung Internet');
      expect(testo(IPHONE_HOME, 'bloccate', 'n')).toContain('Impostazioni → Notifiche → TrovaCampo');
      expect(testo(COMPUTER, 'bloccate', 'n')).toContain('ricarica la pagina');
      expect(istruzioniNotifiche('bloccate', ANDROID)!.riprova).toBeTrue();
    });
  });

  describe('posizione', () => {
    it('bloccata su Android: icona dell indirizzo e autorizzazioni di Chrome', () => {
      const tutto = testo(ANDROID, 'bloccata', 'p');
      expect(tutto).toContain('Autorizzazioni → Posizione → Consenti');
      expect(tutto).toContain('Reimposta autorizzazioni');
      expect(tutto).toContain(
        'App → Chrome → Autorizzazioni → Posizione → «Consenti solo mentre l’app è in uso»',
      );
    });

    it('bloccata su iPhone con Safari: Siti web di Safari e Safari → Posizione', () => {
      const tutto = testo(IPHONE, 'bloccata', 'p');
      expect(tutto).toContain('Privacy e sicurezza → Localizzazione');
      expect(tutto).toContain('Siti web di Safari» → «Mentre usi l’app»');
      expect(tutto).toContain('Safari → Posizione → «Chiedi»');
    });

    it('bloccata su iPhone con Chrome: Localizzazione → Chrome', () => {
      expect(testo(IPHONE_CHROME, 'bloccata', 'p')).toContain('Localizzazione → Chrome');
    });

    it('bloccata sul computer: impostazioni del sito e del sistema', () => {
      expect(testo(COMPUTER, 'bloccata', 'p')).toContain('Privacy e sicurezza → Posizione');
    });

    it('rifiutata: prima Riprova, poi i passi se la richiesta non compare', () => {
      const istruzioni = istruzioniPosizione('rifiutata', ANDROID)!;
      expect(istruzioni.spiegazione).toContain('Riprova');
      expect(istruzioni.passi.length).toBeGreaterThan(0);
    });

    it('non disponibile: accendere la localizzazione', () => {
      expect(testo(ANDROID, 'non-disponibile', 'p')).toContain('accendi «Posizione»');
      expect(testo(IPHONE, 'non-disponibile', 'p')).toContain('Localizzazione: attivala');
    });

    it('scaduta: all aperto o con il Wi-Fi', () => {
      expect(testo(ANDROID, 'scaduta', 'p')).toContain('all’aperto');
      expect(istruzioniPosizione('scaduta', ANDROID)!.riprova).toBeTrue();
    });

    it('nel browser di un app: uscirne', () => {
      const tutto = testo({ ...ANDROID, appInterna: 'Telegram' }, 'bloccata', 'p');
      expect(tutto).toContain('Telegram');
      expect(tutto).toContain('Apri in Chrome');
    });
  });

  it('la riga di stato', () => {
    expect(statoNotifiche('consentite').testo).toBe('consentite');
    expect(statoNotifiche('da-consentire').testo).toBe('da consentire');
    expect(statoNotifiche('bloccate').testo).toBe('bloccate');
    expect(statoNotifiche('iphone-da-installare').testo).toBe('non disponibili qui');
    expect(statoPosizione('consentita')?.testo).toBe('consentita');
    expect(statoPosizione('bloccata')?.testo).toBe('bloccata');
    expect(statoPosizione('sconosciuta')).toBeNull();
  });
});
