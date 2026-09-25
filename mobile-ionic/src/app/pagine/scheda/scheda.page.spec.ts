import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import {
  AlertController,
  NavController,
  ToastController,
  provideIonicAngular,
} from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import { Partita } from '../../modelli/partita';
import { Societa } from '../../modelli/societa';
import { Squadra } from '../../modelli/squadra';
import { AmbientePush } from '../../servizi/ambiente-push';
import { AmministrazioneService } from '../../servizi/amministrazione.service';
import { ricordaAmministratore } from '../../servizi/amministratore-ricordato';
import { AutenticazioneService } from '../../servizi/autenticazione.service';
import { AmbientePushFinto, attendi } from '../../servizi/ambiente-push-finto.spec';
import { NotificheService } from '../../servizi/notifiche.service';
import { SocietaService } from '../../servizi/societa.service';
import { SchedaPage } from './scheda.page';

const SOCIETA: Societa = {
  id: '1',
  siglaSocieta: '',
  nomeSocieta: 'BOREALE',
  comitatoRegionale: 'LAZIO',
  nomeImpianto: 'DON ORIONE',
  indirizzoImpianto: 'VIA DELLA CAMILLUCCIA 120',
  localitaImpianto: 'ROMA',
  provinciaImpianto: 'RM',
  anagraficaImpiantoId: 190,
};

const PARTITA: Partita = {
  dataOra: '2026-09-06T11:00:00+02:00',
  casa: 'BOREALE',
  ospite: 'VIGOR PERCONTI',
  campionato: 'ECCELLENZA',
  ente: 'Regionali',
  girone: 'A',
  giornata: 1,
};

/**
 * Partite, anagrafica e campionati si piegano: la scheda si apre con le
 * partite in vista, e il resto a portata di un tocco.
 */
describe('SchedaPage', () => {
  let fixture: ComponentFixture<SchedaPage>;

  let messaggi: string[];
  /** Le conferme mostrate, e il tasto che chi amministra vi tocca. */
  let conferme: { header: string; message: string }[];
  let risposta: 'destructive' | 'cancel';
  let eliminate: string[];
  let esitoEliminazione: () => ReturnType<AmministrazioneService['elimina']>;
  let navigazioni: string[];
  let accessi: number;
  let entrato: boolean;

  afterEach(() => {
    localStorage.removeItem('trovacampo.squadreSeguite');
    ricordaAmministratore(false);
    sessionStorage.removeItem('trovacampo.eliminaDopoAccesso');
  });

  function apri(societa: Societa, partite: Partita[] = [PARTITA], squadre: Squadra[] = []): HTMLElement {
    messaggi = [];
    conferme = [];
    risposta = 'destructive';
    eliminate = [];
    esitoEliminazione = () => of(undefined);
    navigazioni = [];
    accessi = 0;
    entrato = true;
    TestBed.configureTestingModule({
      imports: [SchedaPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: societa.id }) } },
        },
        {
          provide: SocietaService,
          useValue: {
            perId: () => of(societa),
            squadre: () => of(squadre),
            partite: () => of(partite),
          },
        },
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AmbientePush, useValue: new AmbientePushFinto() },
        {
          provide: AlertController,
          useValue: {
            create: async (opzioni: { header: string; message: string }) => {
              conferme.push(opzioni);
              return {
                present: async () => undefined,
                onDidDismiss: async () => ({ role: risposta }),
              };
            },
          },
        },
        {
          provide: AmministrazioneService,
          useValue: {
            elimina: (id: string) => {
              eliminate.push(id);
              return esitoEliminazione();
            },
          },
        },
        {
          provide: AutenticazioneService,
          useValue: {
            amministratore: () => entrato,
            accedi: async () => {
              accessi++;
              return true;
            },
          },
        },
        {
          provide: NavController,
          useValue: {
            navigateRoot: async (url: string) => {
              navigazioni.push(url);
              return true;
            },
          },
        },
        {
          provide: ToastController,
          useValue: {
            create: async ({ message }: { message: string }) => {
              messaggi.push(message);
              return { present: async () => undefined };
            },
          },
        },
      ],
    });
    fixture = TestBed.createComponent(SchedaPage);
    fixture.componentInstance.ionViewWillEnter();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function sezioni(pagina: HTMLElement): (string | null)[] {
    return Array.from(pagina.querySelectorAll('ion-accordion')).map((a) => a.getAttribute('value'));
  }

  it('mette lo stemma accanto al nome in testa', () => {
    const pagina = apri({ ...SOCIETA, logoUrl: 'https://play.lnd.it/lndimg/7/7-web.png' });
    const stemma = pagina.querySelector('.testata stemma-societa') as HTMLElement;

    expect(stemma.querySelector('img')!.getAttribute('src')).toBe(
      'https://play.lnd.it/lndimg/7/7-web.png',
    );
    expect(stemma.style.getPropertyValue('--stemma-dimensione')).toBe('48px');
    expect(pagina.querySelector('.testata h2')!.textContent).toContain('BOREALE');
  });

  it('senza stemma in testa c è l iniziale del nome', () => {
    const pagina = apri(SOCIETA);
    const stemma = pagina.querySelector('.testata stemma-societa')!;

    expect(stemma.querySelector('img')).toBeNull();
    expect(stemma.querySelector('.segnaposto')!.textContent!.trim()).toBe('B');
  });

  it('mette partite, anagrafica e campionati in tre sezioni pieghevoli', () => {
    const pagina = apri(SOCIETA);

    expect(sezioni(pagina)).toEqual(['partite', 'anagrafica', 'campionati']);
    expect(fixture.componentInstance.sezioniAperte()).toEqual(['partite']);
  });

  it('mostra ogni partita con il riquadro del giorno, le squadre e il campionato', () => {
    const pagina = apri(SOCIETA);
    const partita = pagina.querySelector('.partita')!;

    expect(partita.querySelector('.calendario .numero')?.textContent?.trim()).toBe('6');
    expect(partita.querySelector('.calendario .mese')?.textContent?.trim()).toBe('set');
    expect(partita.querySelector('.casa')?.textContent?.trim()).toBe('BOREALE');
    expect(partita.querySelector('.ospite')?.textContent?.trim()).toBe('VIGOR PERCONTI');
    expect(partita.querySelector('.nome-campionato')?.textContent?.trim()).toBe('ECCELLENZA');
    expect(pagina.querySelector('ion-accordion[value=partite] ion-badge')?.textContent?.trim()).toBe(
      '1',
    );
  });

  it('senza partite in programma lo dice dentro la sezione', () => {
    const pagina = apri(SOCIETA, []);

    expect(pagina.querySelector('ion-accordion[value=partite] .vuoto')?.textContent).toContain(
      'Nessuna partita in programma',
    );
  });

  it('un campo che non viene da presenze non ha la sezione partite, e si apre sui campionati', () => {
    const pagina = apri({ ...SOCIETA, anagraficaImpiantoId: undefined });

    expect(sezioni(pagina)).toEqual(['anagrafica', 'campionati']);
    expect(fixture.componentInstance.sezioniAperte()).toEqual(['campionati']);
  });
  describe('la campanella delle squadre', () => {
    const PRIMA: Squadra = {
      campionato: 'ECCELLENZA',
      ente: 'Regionali',
      stagione: '2026/2027',
      girone: 'A',
      squadra: '',
      fuoriClassifica: false,
      chiave: '7|eccellenza|regionali|',
    };
    const SENZA_CHIAVE: Squadra = { ...PRIMA, campionato: 'UNDER 17', chiave: undefined };

    function campanelle(pagina: HTMLElement): HTMLElement[] {
      return Array.from(pagina.querySelectorAll('ion-accordion[value=campionati] ion-button.segui'));
    }

    it('c è su ogni squadra che il backend sa riconoscere', () => {
      const pagina = apri(SOCIETA, [], [PRIMA, SENZA_CHIAVE]);

      expect(campanelle(pagina).length).toBe(1);
      expect(campanelle(pagina)[0].getAttribute('aria-label')).toBe('Segui ECCELLENZA');
      expect(campanelle(pagina)[0].getAttribute('aria-pressed')).toBe('false');
    });

    it('segue la squadra, e toccata di nuovo smette', async () => {
      const pagina = apri(SOCIETA, [], [PRIMA]);
      const notifiche = TestBed.inject(NotificheService);

      campanelle(pagina)[0].click();
      await attendi();
      fixture.detectChanges();

      expect(notifiche.segue('7|eccellenza|regionali|')).toBeTrue();
      expect(notifiche.seguite()[0]).toEqual({
        chiave: '7|eccellenza|regionali|',
        societa: 'BOREALE',
        societaId: '1',
        campionato: 'ECCELLENZA',
        dettaglio: 'Girone A · Regionali',
      });
      expect(campanelle(pagina)[0].getAttribute('aria-pressed')).toBe('true');
      expect(campanelle(pagina)[0].classList).toContain('attiva');
      // Con l'avviso spento lo dice, con il rimando alla pagina Notifiche.
      expect(messaggi[0]).toContain('accendi le notifiche');

      campanelle(pagina)[0].click();
      await attendi();
      fixture.detectChanges();
      expect(notifiche.segue('7|eccellenza|regionali|')).toBeFalse();
      expect(campanelle(pagina)[0].getAttribute('aria-pressed')).toBe('false');
    });
  });

  describe('il cestino di chi amministra', () => {
    const DOPPIONE: Societa = {
      ...SOCIETA,
      id: '6ab3bfa23ae69c1e8dea9774',
      nomeSocieta: 'POMEZIA CALCIO 1957',
      nomeImpianto: 'DA DESIGNARE (',
      anagraficaSocietaId: 40,
      anagraficaImpiantoId: 900,
    };

    function cestino(pagina: HTMLElement): HTMLElement | null {
      return pagina.querySelector('ion-button.elimina');
    }

    /** Amministrazione e login si caricano al primo tocco, con import dinamici: qualche giro. */
    async function aspetta(): Promise<void> {
      for (let i = 0; i < 20; i++) {
        await attendi();
      }
      fixture.detectChanges();
    }

    async function tocca(pagina: HTMLElement): Promise<void> {
      cestino(pagina)!.click();
      await aspetta();
    }

    it('chi non amministra non lo vede', () => {
      const pagina = apri(DOPPIONE);

      expect(cestino(pagina)).toBeNull();
    });

    it('chi amministra lo vede accanto a Modifica, rosso e con un nome per lo screen reader', () => {
      ricordaAmministratore(true);
      const pagina = apri(DOPPIONE);

      expect(cestino(pagina)).not.toBeNull();
      expect(cestino(pagina)!.getAttribute('color')).toBe('danger');
      expect(cestino(pagina)!.getAttribute('aria-label')).toBe('Elimina società');
    });

    it('chiede conferma dicendo che un campo di presenze non torna, poi elimina e va all elenco', async () => {
      ricordaAmministratore(true);
      const pagina = apri(DOPPIONE);

      await tocca(pagina);

      expect(conferme.length).toBe(1);
      expect(conferme[0].header).toBe('Eliminare POMEZIA CALCIO 1957 – DA DESIGNARE (?');
      expect(conferme[0].message).toContain('non ricomparirà con la sincronizzazione');
      expect(eliminate).toEqual(['6ab3bfa23ae69c1e8dea9774']);
      expect(messaggi).toContain('Società eliminata.');
      expect(navigazioni).toEqual(['/campi']);
    });

    it('per un campo inserito a mano non parla di sincronizzazione', async () => {
      ricordaAmministratore(true);
      const pagina = apri({ ...SOCIETA, anagraficaImpiantoId: undefined });

      await tocca(pagina);

      expect(conferme[0].message).not.toContain('sincronizzazione');
      expect(conferme[0].message).toContain('Non si può annullare');
    });

    it('con Annulla non elimina niente', async () => {
      ricordaAmministratore(true);
      const pagina = apri(DOPPIONE);
      risposta = 'cancel';

      await tocca(pagina);

      expect(conferme.length).toBe(1);
      expect(eliminate).toEqual([]);
      expect(navigazioni).toEqual([]);
    });

    it('se il server rifiuta lo dice e resta sulla scheda', async () => {
      ricordaAmministratore(true);
      const pagina = apri(DOPPIONE);
      esitoEliminazione = () =>
        throwError(() => new Error('Il tuo utente non ha il ruolo trovacampo-admin.'));

      await tocca(pagina);

      expect(messaggi).toContain('Il tuo utente non ha il ruolo trovacampo-admin.');
      expect(navigazioni).toEqual([]);
      expect(fixture.componentInstance.eliminazione()).toBeFalse();
    });

    it('senza login fatto in questa pagina passa prima dal login', async () => {
      ricordaAmministratore(true);
      const pagina = apri(DOPPIONE);
      entrato = false;

      await tocca(pagina);

      // Qui il login finto torna subito; quello vero porterebbe via, e al
      // ritorno la scheda ritroverebbe il segno lasciato in sessionStorage.
      expect(accessi).toBe(1);
      expect(sessionStorage.getItem('trovacampo.eliminaDopoAccesso')).toBeNull();
      expect(eliminate).toEqual(['6ab3bfa23ae69c1e8dea9774']);
    });

    it('di ritorno dal login finisce l accesso e richiede la conferma', async () => {
      ricordaAmministratore(true);
      sessionStorage.setItem('trovacampo.eliminaDopoAccesso', '6ab3bfa23ae69c1e8dea9774');
      apri(DOPPIONE);
      await aspetta();

      expect(accessi).toBeGreaterThan(0);
      expect(conferme.length).toBe(1);
      expect(eliminate).toEqual(['6ab3bfa23ae69c1e8dea9774']);
      expect(sessionStorage.getItem('trovacampo.eliminaDopoAccesso')).toBeNull();
    });
  });
});
