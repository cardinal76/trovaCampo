import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { environment } from '../../../environments/environment';
import { AmbientePush } from '../../servizi/ambiente-push';
import { AmbientePushFinto, attendi } from '../../servizi/ambiente-push-finto.spec';
import { NotificheService } from '../../servizi/notifiche.service';
import { PosizioneService, errorePerCodice } from '../../servizi/posizione.service';
import { SONDE_BROWSER } from '../../servizi/sonde-browser';
import { AGENTI, SondeFinte } from '../../servizi/sonde-finte.spec';
import { NotifichePage, quandoSalvata } from './notifiche.page';

describe('NotifichePage', () => {
  let fixture: ComponentFixture<NotifichePage>;
  let ambiente: AmbientePushFinto;
  let sonde: SondeFinte;
  let gps: jasmine.Spy;
  let http: HttpTestingController;

  afterEach(() => {
    localStorage.removeItem('trovacampo.notifiche');
    localStorage.removeItem('trovacampo.squadreSeguite');
  });

  function apri(
    prepara: (a: AmbientePushFinto, s: SondeFinte) => void = () => undefined,
  ): HTMLElement {
    ambiente = new AmbientePushFinto();
    sonde = new SondeFinte();
    gps = jasmine.createSpy('attuale').and.resolveTo({ lat: 41.9, lng: 12.5 });
    prepara(ambiente, sonde);
    TestBed.configureTestingModule({
      imports: [NotifichePage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AmbientePush, useValue: ambiente },
        { provide: PosizioneService, useValue: { attuale: gps } },
        { provide: SONDE_BROWSER, useValue: sonde },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NotifichePage);
    fixture.componentInstance.ionViewWillEnter();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function interruttore(pagina: HTMLElement, avviso: string): HTMLIonToggleElement {
    return pagina.querySelector(`ion-toggle[data-avviso=${avviso}]`) as HTMLIonToggleElement;
  }

  /** Il riquadro "cosa blocca" delle notifiche o della posizione, se c'è. */
  function riquadro(pagina: HTMLElement, di: 'notifiche' | 'posizione'): HTMLElement | null {
    return pagina.querySelector(`riquadro-diagnosi[data-riquadro=${di}]`);
  }

  /** Il caso mostrato dal riquadro, e la sezione in cui sta ('cima' se fuori dalle sezioni). */
  function caso(pagina: HTMLElement, di: 'notifiche' | 'posizione'): string | null {
    return riquadro(pagina, di)?.querySelector('[data-caso]')?.getAttribute('data-caso') ?? null;
  }

  function sezione(elemento: HTMLElement | null): string {
    return elemento?.closest('section')?.getAttribute('aria-labelledby') ?? 'cima';
  }

  function stato(pagina: HTMLElement, permesso: 'notifiche' | 'posizione'): string | null {
    return (
      pagina.querySelector(`[data-permesso=${permesso}] strong`)?.textContent?.trim() ?? null
    );
  }

  async function aggiorna(): Promise<void> {
    await attendi();
    fixture.detectChanges();
  }

  function tocca(toggle: HTMLIonToggleElement, avviso: 'squadre' | 'vicino', acceso = true) {
    toggle.checked = acceso;
    const evento = {
      target: toggle,
      detail: { checked: acceso },
    } as unknown as CustomEvent<{ checked: boolean }>;
    return avviso === 'squadre'
      ? fixture.componentInstance.cambiaSquadre(evento)
      : fixture.componentInstance.cambiaVicino(evento);
  }

  it('parte con i due avvisi spenti, e spiega come seguire una squadra', () => {
    const pagina = apri();

    expect(interruttore(pagina, 'squadre').checked).toBeFalse();
    expect(interruttore(pagina, 'vicino').checked).toBeFalse();
    expect(interruttore(pagina, 'squadre').disabled).toBeFalse();
    expect(pagina.querySelector('.seguite .vuoto')?.textContent).toContain('campanella');
    expect(pagina.textContent).toContain('non può seguire la tua posizione in sottofondo');
    expect(pagina.querySelector('.avviso')).toBeNull();
    expect(pagina.querySelector('riquadro-diagnosi')).toBeNull();
  });

  describe('riga di stato', () => {
    it('dice cosa manca: notifiche e posizione da consentire', async () => {
      const pagina = apri();
      await aggiorna();

      expect(stato(pagina, 'notifiche')).toBe('da consentire');
      expect(stato(pagina, 'posizione')).toBe('da consentire');
    });

    it('consentite e consentita', async () => {
      const pagina = apri((a, s) => {
        a.permessoAttuale = 'granted';
        s.permessi = { geolocation: 'granted' };
      });
      await aggiorna();

      expect(stato(pagina, 'notifiche')).toBe('consentite');
      expect(stato(pagina, 'posizione')).toBe('consentita');
    });

    it('senza Permissions API non dice niente della posizione, invece di tirare a indovinare', async () => {
      const pagina = apri((_, s) => (s.permessi = null));
      await aggiorna();

      expect(stato(pagina, 'posizione')).toBeNull();
      expect(stato(pagina, 'notifiche')).toBe('da consentire');
    });
  });

  it('su iPhone nel browser spiega come aggiungerla alla Home, con gli interruttori spenti', async () => {
    const pagina = apri((a, s) => {
      a.supportoAttuale = 'iphone-da-installare';
      s.userAgent = AGENTI.iphoneSafari;
    });
    await aggiorna();

    expect(caso(pagina, 'notifiche')).toBe('iphone-da-installare');
    expect(sezione(riquadro(pagina, 'notifiche'))).toBe('cima');
    expect(riquadro(pagina, 'notifiche')?.textContent).toContain('Aggiungi alla schermata Home');
    // Da qui non si sblocca niente: niente Riprova.
    expect(riquadro(pagina, 'notifiche')?.querySelector('.riprova')).toBeNull();
    expect(stato(pagina, 'notifiche')).toBe('non disponibili qui');
    expect(interruttore(pagina, 'squadre').disabled).toBeTrue();
    expect(interruttore(pagina, 'vicino').disabled).toBeTrue();
    // La posizione invece si può dare anche da qui.
    expect(pagina.querySelector('.aggiorna-posizione')?.textContent).toContain('Usa la mia posizione');
  });

  it('il raggio si sceglie con tre pillole, e quella scelta è accesa', async () => {
    const pagina = apri();
    const pillole = () =>
      Array.from(pagina.querySelectorAll<HTMLButtonElement>('.scelta-raggio')).map((p) => [
        p.textContent?.trim(),
        p.getAttribute('aria-checked'),
      ]);

    expect(pillole()).toEqual([
      ['5 km', 'false'],
      ['10 km', 'true'],
      ['20 km', 'false'],
    ]);

    pagina.querySelectorAll<HTMLButtonElement>('.scelta-raggio')[2].click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(pillole()[2]).toEqual(['20 km', 'true']);
    expect(pillole()[1]).toEqual(['10 km', 'false']);
  });

  it('nel browser interno di WhatsApp dice di aprire la pagina in Chrome', async () => {
    const pagina = apri((a, s) => {
      a.supportoAttuale = 'browser-in-app';
      s.userAgent = AGENTI.androidWhatsApp;
    });
    await aggiorna();

    expect(caso(pagina, 'notifiche')).toBe('browser-in-app');
    expect(riquadro(pagina, 'notifiche')?.textContent).toContain('WhatsApp');
    expect(riquadro(pagina, 'notifiche')?.textContent).toContain('Apri in Chrome');
  });

  it('su un browser senza push (navigazione privata) lo dice', () => {
    const pagina = apri((a) => (a.supportoAttuale = 'non-supportato'));

    expect(caso(pagina, 'notifiche')).toBe('non-supportate');
    expect(riquadro(pagina, 'notifiche')?.textContent).toContain('navigazione privata');
  });

  it('con le notifiche bloccate dice dove riattivarle, per Android', () => {
    const pagina = apri((a, s) => {
      a.permessoAttuale = 'denied';
      s.userAgent = AGENTI.androidChrome;
    });

    expect(caso(pagina, 'notifiche')).toBe('bloccate');
    expect(riquadro(pagina, 'notifiche')?.textContent).toContain('Autorizzazioni → Notifiche');
    expect(riquadro(pagina, 'notifiche')?.querySelector('.riprova')).not.toBeNull();
    expect(stato(pagina, 'notifiche')).toBe('bloccate');
  });

  it('con la posizione bloccata per il sito lo dice nella sezione delle vicine', async () => {
    const pagina = apri((_, s) => {
      s.userAgent = AGENTI.androidChrome;
      s.permessi = { geolocation: 'denied' };
    });
    await aggiorna();

    expect(caso(pagina, 'posizione')).toBe('bloccata');
    expect(sezione(riquadro(pagina, 'posizione'))).toBe('titolo-vicine');
    expect(riquadro(pagina, 'posizione')?.textContent).toContain(
      '«Consenti solo mentre l’app è in uso»',
    );
    expect(stato(pagina, 'posizione')).toBe('bloccata');
  });

  it('su iPhone con la posizione negata dà i passi di Safari', async () => {
    const pagina = apri((a, s) => {
      a.supportoAttuale = 'iphone-da-installare';
      s.userAgent = AGENTI.iphoneSafari;
      s.permessi = null;
    });
    gps.and.rejectWith(errorePerCodice(1));
    await fixture.componentInstance.aggiornaPosizione();
    fixture.detectChanges();

    expect(caso(pagina, 'posizione')).toBe('bloccata');
    expect(riquadro(pagina, 'posizione')?.textContent).toContain('Siti web di Safari');
  });

  for (const [codice, atteso, parola] of [
    [2, 'non-disponibile', 'Posizione'],
    [3, 'scaduta', 'all’aperto'],
  ] as const) {
    it(`con l errore ${codice} mostra il caso ${atteso}, e Riprova rilegge la posizione`, async () => {
      const pagina = apri((_, s) => (s.userAgent = AGENTI.androidChrome));
      gps.and.rejectWith(errorePerCodice(codice));
      await fixture.componentInstance.aggiornaPosizione();
      fixture.detectChanges();

      expect(caso(pagina, 'posizione')).toBe(atteso);
      expect(riquadro(pagina, 'posizione')?.textContent).toContain(parola);

      gps.and.resolveTo({ lat: 41.8, lng: 12.4 });
      (riquadro(pagina, 'posizione')!.querySelector('.riprova') as HTMLElement).click();
      await aggiorna();
      await aggiorna();

      expect(riquadro(pagina, 'posizione')).toBeNull();
      expect(pagina.querySelector('.stato-posizione')?.textContent).toContain('Posizione salvata oggi');
    });
  }

  describe('accendendo le partite vicine con le notifiche bloccate', () => {
    it('chiede comunque la posizione, lascia spento e mette il riquadro sotto l interruttore', async () => {
      const pagina = apri((a, s) => {
        a.permessoAttuale = 'denied';
        s.userAgent = AGENTI.androidChrome;
      });
      const toggle = interruttore(pagina, 'vicino');

      await tocca(toggle, 'vicino');
      fixture.detectChanges();

      expect(gps).toHaveBeenCalled();
      expect(toggle.checked).toBeFalse();
      expect(caso(pagina, 'notifiche')).toBe('bloccate');
      expect(sezione(riquadro(pagina, 'notifiche'))).toBe('titolo-vicine');
      expect(pagina.querySelector('.stato-posizione')?.textContent).toContain('Posizione salvata oggi');
      expect(stato(pagina, 'posizione')).toBe('consentita');
    });

    it('sbloccate le notifiche, Riprova riaccende l avviso', async () => {
      const pagina = apri((a) => (a.permessoAttuale = 'denied'));
      await tocca(interruttore(pagina, 'vicino'), 'vicino');
      fixture.detectChanges();

      // Riattivate dalle impostazioni del browser.
      ambiente.permessoAttuale = 'granted';
      (riquadro(pagina, 'notifiche')!.querySelector('.riprova') as HTMLElement).click();
      await attendi();
      http.expectOne(`${environment.apiUrl}/api/notifiche/chiave`).flush({ chiave: 'BChiave' });
      await attendi();
      http.expectOne((r) => r.method === 'PUT').flush(null);
      await aggiorna();

      expect(TestBed.inject(NotificheService).preferenze().avvisoVicino).toBeTrue();
      expect(riquadro(pagina, 'notifiche')).toBeNull();
      expect(stato(pagina, 'notifiche')).toBe('consentite');
    });
  });

  it('Riprova sulle notifiche, senza un avviso da accendere, richiede il permesso', async () => {
    const pagina = apri((a) => a.chiediPermesso.and.resolveTo('default'));
    // Una volta chiusa la richiesta, Riprova la rifà.
    await fixture.componentInstance.riprovaNotifiche();
    expect(ambiente.chiediPermesso).toHaveBeenCalledTimes(1);
    fixture.detectChanges();
    expect(pagina.querySelector('.avviso.errore')?.textContent).toContain('chiuso la richiesta');
  });

  it('elenca le squadre seguite, e si smette di seguirle', async () => {
    localStorage.setItem(
      'trovacampo.squadreSeguite',
      JSON.stringify([
        {
          chiave: '412|eccellenza|regionali|',
          societa: 'LODIGIANI',
          societaId: 'abc',
          campionato: 'ECCELLENZA',
          dettaglio: 'Girone B',
        },
      ]),
    );
    const pagina = apri();

    const seguita = pagina.querySelector('.seguita')!;
    expect(seguita.textContent).toContain('LODIGIANI');
    expect(seguita.textContent).toContain('ECCELLENZA');
    expect(seguita.querySelector('a')?.getAttribute('href')).toBe('/societa/abc');

    (seguita.querySelector('ion-button.smetti') as HTMLElement).click();
    await attendi();
    fixture.detectChanges();
    expect(pagina.querySelector('.seguita')).toBeNull();
  });

  it('se il permesso viene negato l interruttore torna spento e il riquadro compare lì sotto', async () => {
    const pagina = apri((a) => (a.risposta = 'denied'));
    const toggle = interruttore(pagina, 'squadre');

    await tocca(toggle, 'squadre');
    fixture.detectChanges();

    expect(toggle.checked).toBeFalse();
    expect(caso(pagina, 'notifiche')).toBe('bloccate');
    expect(sezione(riquadro(pagina, 'notifiche'))).toBe('titolo-squadre');
    // Accendendo le squadre la posizione non serve: non la si chiede.
    expect(gps).not.toHaveBeenCalled();
  });

  it('accendendo le vicine mostra quando è stata salvata la posizione', async () => {
    const pagina = apri();
    const toggle = interruttore(pagina, 'vicino');

    const fatto = fixture.componentInstance.cambiaVicino({
      target: toggle,
      detail: { checked: true },
    } as unknown as CustomEvent<{ checked: boolean }>);
    await attendi();
    http.expectOne(`${environment.apiUrl}/api/notifiche/chiave`).flush({ chiave: 'BChiave' });
    await attendi();
    http.expectOne((r) => r.method === 'PUT').flush(null);
    await fatto;
    fixture.detectChanges();

    expect(TestBed.inject(NotificheService).preferenze().avvisoVicino).toBeTrue();
    expect(pagina.querySelector('.stato-posizione')?.textContent).toContain('Posizione salvata oggi');
    expect(pagina.querySelector('.aggiorna-posizione')).not.toBeNull();
  });

  it('dice quando è stata salvata la posizione', () => {
    const adesso = new Date(2026, 8, 24, 18, 0);

    expect(quandoSalvata(new Date(2026, 8, 24, 14, 32).toISOString(), adesso)).toBe('oggi alle 14:32');
    expect(quandoSalvata(new Date(2026, 8, 23, 9, 5).toISOString(), adesso)).toBe('ieri alle 9:05');
    expect(quandoSalvata(new Date(2026, 8, 20, 18, 0).toISOString(), adesso)).toBe(
      'il 20 settembre alle 18:00',
    );
    expect(quandoSalvata('non una data', adesso)).toBe('');
  });
});
