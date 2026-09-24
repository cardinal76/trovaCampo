import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { Partita } from '../../modelli/partita';
import { Societa } from '../../modelli/societa';
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

  function apri(societa: Societa, partite: Partita[] = [PARTITA]): HTMLElement {
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
            squadre: () => of([]),
            partite: () => of(partite),
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
});
