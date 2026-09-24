import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ToastController, provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { Partita } from '../../modelli/partita';
import { Societa } from '../../modelli/societa';
import { Squadra } from '../../modelli/squadra';
import { AmbientePush } from '../../servizi/ambiente-push';
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

  afterEach(() => localStorage.removeItem('trovacampo.squadreSeguite'));

  function apri(societa: Societa, partite: Partita[] = [PARTITA], squadre: Squadra[] = []): HTMLElement {
    messaggi = [];
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
});
