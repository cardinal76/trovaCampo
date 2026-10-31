import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { Prospect } from '../../modelli/prospect';
import { AutenticazioneService } from '../../servizi/autenticazione.service';
import { ProspectService, StatoImportazioneProspect } from '../../servizi/prospect.service';
import { ProspectPage } from './prospect.page';

function prospect(cf: string, valori: Partial<Prospect>): Prospect {
  return {
    codiceFiscale: cf,
    denominazione: `ASD ${cf}`,
    calcio: true,
    lat: 41.9,
    lng: 12.5,
    ...valori,
  };
}

const PROSPECT: Prospect[] = [
  prospect('A', { regione: 'Lazio', organismi: ['FIGC', 'CSI'], discipline: ['Calcio (a 11)'] }),
  prospect('B', { regione: 'Lazio', organismi: ['FIGC'], discipline: ['Calcio a 5'], lat: 41.5 }),
  prospect('C', { regione: 'Toscana', organismi: ['UISP'], discipline: ['Calcio (a 11)'], lat: 43.7, lng: 11.2 }),
];

describe('ProspectPage', () => {
  let fixture: ComponentFixture<ProspectPage>;
  let pagina: ProspectPage;
  let amministratore: ReturnType<typeof signal<boolean>>;
  let accedi: jasmine.Spy;
  let servizio: jasmine.SpyObj<ProspectService>;

  beforeEach(() => {
    amministratore = signal(true);
    accedi = jasmine.createSpy('accedi').and.resolveTo(true);
    servizio = jasmine.createSpyObj<ProspectService>('ProspectService', [
      'sullaMappa',
      'stato',
      'avvia',
      'ferma',
      'riprendi',
      'esporta',
      'copertura',
      'statoGeocodifica',
      'collega',
      'geocodifica',
      'importaOpenData',
    ]);
    servizio.copertura.and.returnValue(
      of({
        prospectCalcioLazio: 823,
        collegati: 450,
        conCoordinate: 430,
        copertura: 54.7,
        perFonte: { CAMPO_TROVACAMPO: 400, OPEN_DATA: 50 },
        perConfidenza: {},
      }),
    );
    servizio.statoGeocodifica.and.returnValue(
      of({ inCorso: false, daCercare: 0, cercate: 0, trovate: 0, errori: 0, messaggio: 'mai' }),
    );
    servizio.sullaMappa.and.returnValue(of(PROSPECT));
    servizio.stato.and.returnValue(
      of({
        inCorso: false,
        ultimo: { stato: 'mai', pagine: 0, lette: 0, nuove: 0, dettagli: 0 },
        regioni: [],
      }),
    );

    TestBed.configureTestingModule({
      imports: [ProspectPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        {
          provide: AutenticazioneService,
          useValue: { accedi, amministratore, nome: signal('mario'), ruoli: signal([]) },
        },
        { provide: ProspectService, useValue: servizio },
      ],
    });
  });

  afterEach(() => fixture?.destroy());

  async function crea(): Promise<void> {
    fixture = TestBed.createComponent(ProspectPage);
    pagina = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('apre il login di Keycloak appena si entra', async () => {
    await crea();
    expect(accedi).toHaveBeenCalled();
  });

  it('senza il ruolo trovacampo-admin non chiede i dati e dice cosa manca', async () => {
    amministratore.set(false);
    await crea();

    expect(servizio.sullaMappa).not.toHaveBeenCalled();
    expect(servizio.stato).not.toHaveBeenCalled();
    const testo = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(testo).toContain('trovacampo-admin');
    expect((fixture.nativeElement as HTMLElement).querySelector('.mappa')).toBeNull();
  });

  it('se il login non risponde non mostra niente della lista', async () => {
    accedi.and.rejectWith(new Error('keycloak giù'));
    await crea();

    expect(pagina.accesso()).toBe('errore');
    expect(servizio.sullaMappa).not.toHaveBeenCalled();
  });

  it('con il ruolo mostra tutti i prospect sulla mappa', async () => {
    await crea();

    expect(pagina.visibili().length).toBe(3);
    expect((fixture.nativeElement as HTMLElement).querySelector('.mappa')).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.conteggio')?.textContent).toContain(
      '3',
    );
  });

  it('le opzioni dei filtri vengono dai prospect', async () => {
    await crea();

    expect(pagina.opzioni().regioni).toEqual(['Lazio', 'Toscana']);
    expect(pagina.opzioni().organismi).toEqual(['CSI', 'FIGC', 'UISP']);
    expect(pagina.opzioni().discipline).toEqual(['Calcio (a 11)', 'Calcio a 5']);
  });

  it('i filtri per regione, disciplina e organismo si sommano', async () => {
    await crea();

    pagina.filtra('regione', 'Lazio');
    expect(pagina.visibili().map((p) => p.codiceFiscale)).toEqual(['A', 'B']);

    pagina.filtra('organismo', 'CSI');
    expect(pagina.visibili().map((p) => p.codiceFiscale)).toEqual(['A']);

    pagina.filtra('organismo', '');
    pagina.filtra('disciplina', 'Calcio a 5');
    expect(pagina.visibili().map((p) => p.codiceFiscale)).toEqual(['B']);

    pagina.filtra('regione', null);
    pagina.filtra('disciplina', 'Calcio (a 11)');
    expect(pagina.visibili().map((p) => p.codiceFiscale)).toEqual(['A', 'C']);

    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.conteggio')?.textContent).toContain(
      '2',
    );
  });

  it('«Importa il Lazio» chiede solo la regione 12', async () => {
    servizio.avvia.and.returnValue(
      of({ inCorso: true, ultimo: { stato: 'in corso', pagine: 0, lette: 0, nuove: 0, dettagli: 0 }, regioni: [] }),
    );
    await crea();

    pagina.importaLazio();

    expect(servizio.avvia).toHaveBeenCalledWith([12], undefined, 'CALCIO');
    expect(pagina.stato()?.inCorso).toBeTrue();
  });

  it('mostra la copertura per fonte e lancia OpenStreetMap', async () => {
    servizio.geocodifica.and.returnValue(
      of({
        avviato: true,
        messaggio: 'Ricerca avviata su 300 società',
        stato: { inCorso: true, daCercare: 300, cercate: 0, trovate: 0, errori: 0, messaggio: 'In corso' },
      }),
    );
    await crea();

    const testo = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(testo).toContain('su 823');
    expect(testo).toContain('censimento impianti (open data): 50');

    pagina.cercaSuOsm();
    expect(pagina.geocodifica()?.inCorso).toBeTrue();
    expect(pagina.avviso()).toBe('Ricerca avviata su 300 società');
  });

  describe('stato dell\'importazione', () => {
    const base: StatoImportazioneProspect = {
      inCorso: true,
      fase: 'in corso',
      regioneCorrente: 'Campania',
      richiesteOggi: 1234,
      bloccata: false,
      riprendibile: false,
      opzioni: { regioni: [], dettagli: 'FIGC' },
      ritmo: {
        pausaMs: 3000,
        jitterMs: 1000,
        finestra: '22:00-07:00',
        fuso: 'Europe/Rome',
        tettoGiornaliero: 7000,
        richiestePerGiorno: 7000,
      },
      stima: { pagine: 600, dettagli: 7200, richieste: 7800, secondi: 96 * 3600, giorni: 4 },
      ultimo: { stato: 'in corso', pagine: 40, lette: 4000, nuove: 3900, dettagli: 480, dettagliSaltati: 12 },
      regioni: [],
    };

    function testo(): string {
      return (fixture.nativeElement as HTMLElement).textContent ?? '';
    }

    function pulsante(classe: string): HTMLIonButtonElement {
      return (fixture.nativeElement as HTMLElement).querySelector(`ion-button.${classe}`)!;
    }

    it('mostra fase, regione corrente, richieste di oggi e stima', async () => {
      servizio.stato.and.returnValue(of(base));
      await crea();

      expect(testo()).toContain('In corso');
      expect(testo()).toContain('Regione: Campania');
      expect(testo()).toContain('Richieste oggi: 1234');
      expect(testo()).toContain('su 7000');
      expect(testo()).toContain('solo affiliate FIGC');
      expect(testo()).toContain('22:00-07:00');
      expect(testo()).toContain('circa 4 giorni');
      expect(testo()).toContain('12 saltati perché recenti');
      expect(pulsante('ferma').disabled).toBeFalse();
      expect(pulsante('riprendi').disabled).toBeTrue();
    });

    it('dice quando è in pausa per la finestra oraria', async () => {
      servizio.stato.and.returnValue(
        of({ ...base, fase: 'in pausa: finestra oraria', pausaFinoA: '2026-10-31T21:00:00Z' }),
      );
      await crea();

      expect(testo()).toContain('fuori dalla finestra oraria (22:00-07:00), riparte da sola');
    });

    it('dice quando è in pausa per il tetto giornaliero', async () => {
      servizio.stato.and.returnValue(
        of({ ...base, fase: 'in pausa: tetto giornaliero', richiesteOggi: 7000 }),
      );
      await crea();

      expect(testo()).toContain('raggiunto il tetto di 7000 richieste');
    });

    it('fermo per un blocco lo dice, non riparte da solo, e «Riprendi» lo chiede una persona', async () => {
      const bloccata: StatoImportazioneProspect = {
        ...base,
        inCorso: false,
        fase: 'bloccata',
        bloccata: true,
        riprendibile: true,
        motivo: 'Il registro ha risposto 429 a /api/istruttoria/lista/',
        bloccataIl: '2026-10-30T23:10:00Z',
      };
      servizio.stato.and.returnValue(of(bloccata));
      servizio.riprendi.and.returnValue(of({ ...base, inCorso: true }));
      await crea();

      expect(testo()).toContain('Ferma per un blocco del registro');
      expect(testo()).toContain('429');
      expect((fixture.nativeElement as HTMLElement).querySelector('.blocco')).not.toBeNull();
      expect(pulsante('ferma').disabled).toBeTrue();
      expect(pulsante('riprendi').disabled).toBeFalse();
      expect(servizio.riprendi).not.toHaveBeenCalled();

      pagina.riprendi();
      expect(servizio.riprendi).toHaveBeenCalled();
      expect(pagina.stato()?.inCorso).toBeTrue();
    });

    it('«Ferma» chiama il backend', async () => {
      servizio.stato.and.returnValue(of(base));
      servizio.ferma.and.returnValue(of({ ...base, inCorso: false, fase: 'fermata', riprendibile: true }));
      await crea();

      pagina.ferma();
      fixture.detectChanges();

      expect(servizio.ferma).toHaveBeenCalled();
      expect(testo()).toContain('Fermata');
    });

    it('lancia con l\'ambito dei dettagli scelto', async () => {
      servizio.avvia.and.returnValue(of(base));
      await crea();

      pagina.ambito.set('TUTTE');
      pagina.importaItalia();

      expect(servizio.avvia).toHaveBeenCalledWith([], undefined, 'TUTTE');
    });
  });
});
