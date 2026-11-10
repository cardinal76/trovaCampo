import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { Partita } from '../../../modelli/partita';
import { Risultato } from '../../../modelli/risultato';
import { Campionato, Societa } from '../../../modelli/societa';
import { Squadra } from '../../../modelli/squadra';
import { SezioneAnagraficaComponent } from './sezione-anagrafica.component';
import { SezioneCampionatiComponent } from './sezione-campionati.component';
import { SezionePartiteComponent } from './sezione-partite.component';
import { SezioneRisultatiComponent } from './sezione-risultati.component';
import { Stato, StatoSezioneRisultati } from './stati';

const SOCIETA: Societa = {
  id: '1',
  siglaSocieta: '',
  nomeSocieta: 'BOREALE',
  comitatoRegionale: 'LAZIO',
  nomeImpianto: 'DON ORIONE',
  indirizzoImpianto: 'VIA DELLA CAMILLUCCIA 120',
  localitaImpianto: 'ROMA',
  provinciaImpianto: 'RM',
};

function testo(fixture: ComponentFixture<unknown>): string {
  return (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

describe('SezioneAnagraficaComponent', () => {
  @Component({
    imports: [SezioneAnagraficaComponent],
    template: `<div schedaAnagrafica [societa]="societa()" [conSquadre]="conSquadre()"></div>`,
  })
  class Host {
    readonly societa = signal<Societa>(SOCIETA);
    readonly conSquadre = signal(false);
  }

  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideIonicAngular()],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it("senza dati di anagrafica lo dice, e per un campo inserito a mano dice perche'", () => {
    expect(testo(fixture)).toContain(
      'Anagrafica non ancora disponibile per questo campo (inserito manualmente).',
    );
  });

  it("con delle squadre nei comunicati il motivo e' un altro", () => {
    fixture.componentInstance.conSquadre.set(true);
    fixture.detectChanges();

    expect(testo(fixture)).toContain('Presidente, sede e contatti non sono ancora stati inseriti.');
  });

  it('con i dati mostra solo le voci presenti', () => {
    fixture.componentInstance.societa.set({
      ...SOCIETA,
      presidente: 'Mario Rossi',
      telefono: '06 123',
    });
    fixture.detectChanges();

    const contenuto = testo(fixture);
    // il comitato regionale c'e' sempre nella societa' di prova
    for (const presente of ['Comitato regionale', 'Presidente', 'Telefono']) {
      expect(contenuto).toContain(presente);
    }
    for (const assente of ['Matricola', 'Fax', 'Email', 'Sito web', 'Indirizzo sede']) {
      expect(contenuto).not.toContain(assente);
    }
  });

  it("i prezzi della scuola calcio: assente, no, si', si' con prezzi", () => {
    const prezzi = (s: Partial<Societa>) => {
      fixture.componentInstance.societa.set({ ...SOCIETA, presidente: 'X', ...s });
      fixture.detectChanges();
      return testo(fixture);
    };

    expect(prezzi({})).not.toContain('Scuola calcio');
    expect(prezzi({ scuolaCalcio: false })).toMatch(/Scuola calcio\s*No/);
    expect(prezzi({ scuolaCalcio: true })).toMatch(/Scuola calcio\s*Sì/);
    expect(prezzi({ scuolaCalcio: true, prezziScuolaCalcio: '300 euro' })).toContain(
      'Sì — 300 euro',
    );
  });
});

describe('SezionePartiteComponent', () => {
  const PARTITA: Partita = {
    dataOra: '2026-09-06T11:00:00+02:00',
    casa: 'BOREALE',
    ospite: 'VIGOR PERCONTI',
    campionato: 'ECCELLENZA',
    ente: 'Regionali',
    girone: 'A',
    giornata: 1,
  };

  @Component({
    imports: [SezionePartiteComponent],
    template: `<div schedaPartite [stato]="stato()" [partite]="partite()"></div>`,
  })
  class Host {
    readonly stato = signal<Stato>('completata');
    readonly partite = signal<Partita[]>([PARTITA]);
  }

  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideIonicAngular()],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  const righe = () => (fixture.nativeElement as HTMLElement).querySelectorAll('li.partita');

  it('una riga per partita, con squadre e campionato', () => {
    expect(righe().length).toBe(1);
    expect(testo(fixture)).toContain('BOREALE');
    expect(testo(fixture)).toContain('VIGOR PERCONTI');
    expect(testo(fixture)).toContain('ECCELLENZA');
    expect(testo(fixture)).toContain('Girone A');
  });

  it('una partita in diretta porta il badge LIVE e il punteggio al posto del vs', () => {
    fixture.componentInstance.partite.set([{ ...PARTITA, live: { golCasa: 2, golOspite: 1 } }]);
    fixture.detectChanges();

    expect(testo(fixture)).toContain('LIVE');
    expect(testo(fixture)).toMatch(/2\s*[–-]\s*1/);
    expect(testo(fixture)).not.toContain(' vs ');
  });

  it('senza partite dice per quanti giorni ha guardato', () => {
    fixture.componentInstance.partite.set([]);
    fixture.detectChanges();

    expect(testo(fixture)).toMatch(/Nessuna partita in programma qui nei prossimi \d+ giorni\./);
  });

  it("mentre carica mostra l'attesa, e se fallisce un messaggio", () => {
    fixture.componentInstance.stato.set('caricamento');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('ion-spinner')).not.toBeNull();

    fixture.componentInstance.stato.set('errore');
    fixture.detectChanges();
    expect(testo(fixture)).toContain('Le partite non sono disponibili in questo momento');
  });
});

describe('SezioneRisultatiComponent', () => {
  const VITTORIA: Risultato = {
    squadraIscrittaId: 50,
    data: '2026-10-11T10:30:00+02:00',
    campionato: 'UNDER 17 PROVINCIALE',
    ente: 'Roma',
    girone: 'A',
    giornata: 3,
    squadra: '',
    casa: 'BOREALE',
    ospite: 'ALTRA A.S.D.',
    golCasa: 2,
    golOspite: 1,
    inCasa: true,
    esito: 'VITTORIA',
  };

  @Component({
    imports: [SezioneRisultatiComponent],
    template: `<div schedaRisultati [stato]="stato()" [risultati]="risultati()"></div>`,
  })
  class Host {
    readonly stato = signal<StatoSezioneRisultati>('completata');
    readonly risultati = signal<Risultato[]>([VITTORIA]);
  }

  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideIonicAngular()],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  it("una riga per squadra, con il bollino dell'esito e il punteggio", () => {
    const bollino = (fixture.nativeElement as HTMLElement).querySelector('.bollino');
    expect(bollino?.textContent?.trim()).toBe('V');
    expect(bollino?.classList.contains('vittoria')).toBeTrue();
    expect(testo(fixture)).toMatch(/2\s*[–-]\s*1/);
  });

  it('nel derby la stessa partita compare due volte, una per squadra', () => {
    fixture.componentInstance.risultati.set([
      VITTORIA,
      { ...VITTORIA, squadraIscrittaId: 51, esito: 'SCONFITTA' },
    ]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('li.risultato').length).toBe(2);
  });

  it("chiusa o in caricamento mostra l'attesa; assenti ed errore dicono cose diverse", () => {
    for (const stato of ['chiusa', 'caricamento'] as const) {
      fixture.componentInstance.stato.set(stato);
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).querySelector('ion-spinner')).not.toBeNull();
    }

    fixture.componentInstance.stato.set('assenti');
    fixture.detectChanges();
    expect(testo(fixture)).toContain('non sono ancora disponibili per questa società');
    expect(testo(fixture)).not.toContain('riprova');

    fixture.componentInstance.stato.set('errore');
    fixture.detectChanges();
    expect(testo(fixture)).toContain('riprova tra poco');
  });

  it('senza risultati lo dice', () => {
    fixture.componentInstance.risultati.set([]);
    fixture.detectChanges();

    expect(testo(fixture)).toContain('Nessuna partita giocata finora in questa stagione.');
  });
});

describe('SezioneCampionatiComponent', () => {
  const SQUADRA: Squadra = {
    campionato: 'ECCELLENZA',
    ente: 'Regionali',
    stagione: '2026/2027',
    girone: 'A',
    squadra: '',
    fuoriClassifica: false,
    campo: 'ALTRO CAMPO',
    chiave: '42|1|',
  };
  const AGONISTICA: Campionato = {
    descrizione: 'UNDER 17',
    girone: 'B',
    comitato: 'LAZIO',
    tipo: 'Agonistica',
  };

  @Component({
    imports: [SezioneCampionatiComponent],
    template: `
      <div
        schedaCampionati
        [stato]="stato()"
        [squadre]="squadre()"
        [campionati]="campionati()"
        [agonistica]="agonistica()"
        [scuolaCalcio]="[]"
        nomeImpianto="DON ORIONE"
        [chiaviSeguite]="seguite()"
        (cambiaSegui)="toccate.push($event)"
      ></div>
    `,
  })
  class Host {
    readonly stato = signal<Stato>('completata');
    readonly squadre = signal<Squadra[]>([SQUADRA]);
    readonly campionati = signal<Campionato[]>([]);
    readonly agonistica = signal<Campionato[]>([]);
    readonly seguite = signal<ReadonlySet<string>>(new Set());
    readonly toccate: Squadra[] = [];
  }

  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideIonicAngular()],
    }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  const campanella = () =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('ion-button.segui');

  it("mostra la squadra, il suo campionato e il campo se e' diverso da quello della societa'", () => {
    expect(testo(fixture)).toContain('ECCELLENZA');
    expect(testo(fixture)).toContain('Gioca in casa a ALTRO CAMPO');
  });

  it('la campanella emette la squadra toccata, e dice se la si segue', () => {
    expect(campanella()?.getAttribute('aria-pressed')).toBe('false');
    expect(campanella()?.getAttribute('aria-label')).toBe('Segui ECCELLENZA');

    campanella()!.click();
    expect(fixture.componentInstance.toccate).toEqual([SQUADRA]);

    fixture.componentInstance.seguite.set(new Set(['42|1|']));
    fixture.detectChanges();
    expect(campanella()?.getAttribute('aria-pressed')).toBe('true');
    expect(campanella()?.getAttribute('aria-label')).toBe('Smetti di seguire ECCELLENZA');
  });

  it('una squadra senza chiave non ha la campanella', () => {
    fixture.componentInstance.squadre.set([{ ...SQUADRA, chiave: undefined }]);
    fixture.detectChanges();

    expect(campanella()).toBeNull();
  });

  it("se il portale non risponde lo dice, per ogni squadra con l'avviso", () => {
    fixture.componentInstance.squadre.set([
      { ...SQUADRA, risultatiDaComunicato: { numero: 18, data: '2026-10-09', ente: 'Roma' } },
    ]);
    fixture.detectChanges();

    expect(testo(fixture)).toContain('I risultati sono aggiornati ai Comunicati Ufficiali.');
    expect(testo(fixture)).toContain('Comunicato Ufficiale n. 18 del 09/10');
  });

  it("senza squadre ne' campionati lo dice; con i campionati agonistici no", () => {
    fixture.componentInstance.squadre.set([]);
    fixture.detectChanges();
    expect(testo(fixture)).toContain('Nessun campionato nei comunicati letti finora.');

    fixture.componentInstance.campionati.set([AGONISTICA]);
    fixture.componentInstance.agonistica.set([AGONISTICA]);
    fixture.detectChanges();
    expect(testo(fixture)).not.toContain('Nessun campionato');
    expect(testo(fixture)).toContain('Agonistica');
    expect(testo(fixture)).toContain('Girone B · Comitato LAZIO');
  });

  it("mentre carica mostra l'attesa, e se fallisce un messaggio", () => {
    fixture.componentInstance.stato.set('caricamento');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('ion-spinner')).not.toBeNull();

    fixture.componentInstance.stato.set('errore');
    fixture.detectChanges();
    expect(testo(fixture)).toContain('I campionati non sono disponibili in questo momento');
  });
});
