import { TestBed } from '@angular/core/testing';
import { DiagnosiService, casoPosizione } from './diagnosi.service';
import { ErrorePosizione, errorePerCodice } from './posizione.service';
import { SONDE_BROWSER } from './sonde-browser';
import { AGENTI, SondeFinte } from './sonde-finte.spec';

describe('DiagnosiService', () => {
  function diagnosi(sonde: SondeFinte): DiagnosiService {
    TestBed.configureTestingModule({ providers: [{ provide: SONDE_BROWSER, useValue: sonde }] });
    return TestBed.inject(DiagnosiService);
  }

  describe('notifiche', () => {
    it('Chrome con il permesso non ancora chiesto: da consentire', () => {
      expect(diagnosi(new SondeFinte()).notifiche()).toBe('da-consentire');
    });

    it('permesso dato: consentite', () => {
      const sonde = new SondeFinte();
      sonde.notifiche = 'granted';
      expect(diagnosi(sonde).notifiche()).toBe('consentite');
    });

    it('permesso negato: bloccate', () => {
      const sonde = SondeFinte.androidChrome();
      sonde.notifiche = 'denied';
      expect(diagnosi(sonde).notifiche()).toBe('bloccate');
    });

    it('iPhone con Safari non dalla schermata Home', () => {
      const servizio = diagnosi(SondeFinte.iphoneSafari());
      expect(servizio.notifiche()).toBe('iphone-da-installare');
      expect(servizio.piattaforma.sistema).toBe('ios');
    });

    it('browser interno di WhatsApp', () => {
      const sonde = SondeFinte.androidChrome();
      sonde.userAgent = AGENTI.androidWhatsApp;
      sonde.pushManager = false;
      const servizio = diagnosi(sonde);
      expect(servizio.notifiche()).toBe('browser-in-app');
      expect(servizio.piattaforma.appInterna).toBe('WhatsApp');
    });

    it('senza push (navigazione privata di Firefox): non supportate', () => {
      const sonde = new SondeFinte();
      sonde.serviceWorker = false;
      expect(diagnosi(sonde).notifiche()).toBe('non-supportate');
    });

    it('iscrizione al servizio push fallita: non supportate, finché il permesso non cambia', () => {
      const sonde = new SondeFinte();
      sonde.notifiche = 'granted';
      const servizio = diagnosi(sonde);

      servizio.segnalaIscrizioneFallita();
      expect(servizio.notifiche()).toBe('non-supportate');
      servizio.segnalaIscrizioneRiuscita();
      expect(servizio.notifiche()).toBe('consentite');
    });

    it('rilegge il permesso cambiato dalle impostazioni', () => {
      const sonde = new SondeFinte();
      sonde.notifiche = 'denied';
      const servizio = diagnosi(sonde);
      expect(servizio.notifiche()).toBe('bloccate');

      sonde.notifiche = 'granted';
      servizio.aggiornaNotifiche();
      expect(servizio.notifiche()).toBe('consentite');
    });
  });

  describe('posizione', () => {
    it('senza richieste la dice come la Permissions API', async () => {
      const sonde = new SondeFinte();
      const servizio = diagnosi(sonde);
      expect(servizio.posizione()).toBe('sconosciuta');

      await servizio.aggiorna();
      expect(servizio.posizione()).toBe('da-consentire');

      sonde.permessi = { geolocation: 'granted' };
      await servizio.aggiorna();
      expect(servizio.posizione()).toBe('consentita');
    });

    it('bloccata per il sito: il browser non la chiede più', async () => {
      const sonde = new SondeFinte();
      sonde.permessi = { geolocation: 'denied' };
      const servizio = diagnosi(sonde);

      await servizio.aggiorna();
      expect(servizio.posizione()).toBe('bloccata');
    });

    it('negata alla richiesta ma il browser la può richiedere: rifiutata', async () => {
      const servizio = diagnosi(new SondeFinte());

      await servizio.esitoPosizione(errorePerCodice(1));
      expect(servizio.posizione()).toBe('rifiutata');
    });

    it('negata e bloccata per il sito', async () => {
      const sonde = new SondeFinte();
      sonde.permessi = { geolocation: 'denied' };
      const servizio = diagnosi(sonde);

      await servizio.esitoPosizione(errorePerCodice(1));
      expect(servizio.posizione()).toBe('bloccata');
    });

    it('negata senza Permissions API (Safari vecchio): bloccata, con le istruzioni per sbloccarla', async () => {
      const sonde = SondeFinte.iphoneSafari();
      sonde.permessi = null;
      const servizio = diagnosi(sonde);

      await servizio.esitoPosizione(errorePerCodice(1));
      expect(servizio.posizione()).toBe('bloccata');
    });

    it('Permissions API che rifiuta la domanda: senza richieste resta sconosciuta', async () => {
      const sonde = SondeFinte.iphoneSafari();
      sonde.permessi = { geolocation: null };
      const servizio = diagnosi(sonde);

      await servizio.aggiorna();
      expect(servizio.posizione()).toBe('sconosciuta');
    });

    it('localizzazione spenta: permesso dato, posizione non disponibile', async () => {
      const sonde = SondeFinte.androidChrome();
      sonde.permessi = { geolocation: 'granted' };
      const servizio = diagnosi(sonde);

      await servizio.esitoPosizione(errorePerCodice(2));
      expect(servizio.posizione()).toBe('non-disponibile');
    });

    it('tempo scaduto', async () => {
      const servizio = diagnosi(new SondeFinte());

      await servizio.esitoPosizione(errorePerCodice(3));
      expect(servizio.posizione()).toBe('scaduta');
    });

    it('sbloccata dalle impostazioni dopo un no: consentita', async () => {
      const sonde = new SondeFinte();
      sonde.permessi = { geolocation: 'denied' };
      const servizio = diagnosi(sonde);
      await servizio.esitoPosizione(errorePerCodice(1));

      sonde.permessi = { geolocation: 'granted' };
      await servizio.aggiorna();
      expect(servizio.posizione()).toBe('consentita');
    });

    it('arrivata: consentita anche senza Permissions API', async () => {
      const sonde = SondeFinte.iphoneSafari();
      sonde.permessi = null;
      const servizio = diagnosi(sonde);

      await servizio.esitoPosizione(null);
      expect(servizio.posizione()).toBe('consentita');
    });

    it('pagina non in HTTPS, o browser senza geolocalizzazione', async () => {
      expect(casoPosizione(false, true, 'prompt', null)).toBe('non-sicura');
      expect(casoPosizione(true, false, 'prompt', null)).toBe('non-supportata');
      expect(
        casoPosizione(true, true, null, new ErrorePosizione('non-sicura', '').motivo),
      ).toBe('non-sicura');
    });

    it('un errore sconosciuto vale come posizione non disponibile', async () => {
      const servizio = diagnosi(new SondeFinte());

      await servizio.esitoPosizione(new Error('boh'));
      expect(servizio.posizione()).toBe('non-disponibile');
    });
  });
});
