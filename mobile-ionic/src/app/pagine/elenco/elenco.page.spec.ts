import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { Societa } from '../../modelli/societa';
import { ricordaAmministratore } from '../../servizi/amministratore-ricordato';
import { CampiCambiatiService } from '../../servizi/campi-cambiati.service';
import { ProvinciaSceltaService } from '../../servizi/provincia-scelta.service';
import { SocietaService } from '../../servizi/societa.service';
import { ElencoPage } from './elenco.page';

function campo(id: string, nomeSocieta: string, valori: Partial<Societa> = {}): Societa {
  return {
    id,
    siglaSocieta: '',
    nomeSocieta,
    comitatoRegionale: '',
    nomeImpianto: `Campo ${nomeSocieta}`,
    indirizzoImpianto: 'Via Roma 1',
    localitaImpianto: 'Roma',
    provinciaImpianto: 'RM',
    ...valori,
  };
}

const CAMPI: Societa[] = [
  campo('1', 'Certosa', { lat: 41.89, lng: 12.48 }),
  campo('2', 'Virtus Ostia'),
  campo('3', 'Tor di Quinto', { lat: 41.95, lng: 12.47, posizioneApprossimata: true }),
  campo('4', 'Cinecittà Bettini', { lat: 41.85, lng: 12.57 }),
  campo('5', 'Latina Calcio', { localitaImpianto: 'Latina', provinciaImpianto: 'LT' }),
  campo('6', 'Sconosciuta', { localitaImpianto: '', provinciaImpianto: '' }),
];

describe('ElencoPage', () => {
  let fixture: ComponentFixture<ElencoPage>;
  let pagina: ElencoPage;
  /** Quello che risponde il backend: i test di ricarica lo cambiano. */
  let scaricati: Societa[];

  function crea(): void {
    fixture = TestBed.createComponent(ElencoPage);
    pagina = fixture.componentInstance;
    fixture.detectChanges();
  }

  function interruttore(): HTMLElement | null {
    return fixture.nativeElement.querySelector('.filtro-posizione');
  }

  function nomi(): string[] {
    return pagina.filtrati().map((societa) => societa.nomeSocieta);
  }

  beforeEach(() => {
    scaricati = CAMPI;
    TestBed.configureTestingModule({
      imports: [ElencoPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        { provide: SocietaService, useValue: { tutti: () => of(scaricati) } },
      ],
    });
  });

  afterEach(() => {
    ricordaAmministratore(false);
    localStorage.removeItem('trovacampo.provincia');
  });

  function selettoreProvincia(): HTMLElement | null {
    return fixture.nativeElement.querySelector('ion-select.filtro-provincia');
  }

  it('ogni riga ha lo stemma a sinistra, o l iniziale quando manca', () => {
    TestBed.overrideProvider(SocietaService, {
      useValue: {
        tutti: () =>
          of([
            campo('1', 'A.S.D. PALOCCO', { logoUrl: 'https://play.lnd.it/lndimg/1/1-web.png' }),
            campo('2', 'A.S.D. ACCADEMIA SPORTING ROMA'),
          ]),
      },
    });
    crea();

    const stemmi = Array.from(
      fixture.nativeElement.querySelectorAll('ion-item stemma-societa'),
    ) as HTMLElement[];
    expect(stemmi.length).toBe(2);
    expect(stemmi.every((stemma) => stemma.getAttribute('slot') === 'start')).toBeTrue();
    expect(stemmi[0].querySelector('img')!.getAttribute('src')).toBe(
      'https://play.lnd.it/lndimg/1/1-web.png',
    );
    expect(stemmi[0].style.getPropertyValue('--stemma-dimensione')).toBe('36px');
    expect(stemmi[1].querySelector('img')).toBeNull();
    expect(stemmi[1].querySelector('.segnaposto')!.textContent!.trim()).toBe('A');
  });

  it('chi non amministra non vede l interruttore', () => {
    crea();

    expect(interruttore()).toBeNull();
  });

  it('chi amministra vede l interruttore', () => {
    ricordaAmministratore(true);
    crea();

    expect(interruttore()).not.toBeNull();
  });

  it('acceso lascia solo i campi senza coordinate o col segnaposto approssimato', () => {
    ricordaAmministratore(true);
    crea();

    pagina.cambiaSoloSenzaPosizione(true);

    expect(nomi()).toEqual(['Virtus Ostia', 'Tor di Quinto', 'Latina Calcio', 'Sconosciuta']);

    pagina.cambiaSoloSenzaPosizione(false);

    expect(nomi().length).toBe(CAMPI.length);
  });

  it('la pillola si accende al tocco e lo dice anche a chi usa lo screen reader', () => {
    ricordaAmministratore(true);
    crea();
    const pillola = interruttore() as HTMLButtonElement;

    expect(pillola.getAttribute('aria-pressed')).toBe('false');
    pillola.click();
    fixture.detectChanges();

    expect(pagina.soloSenzaPosizione()).toBeTrue();
    expect(pillola.getAttribute('aria-pressed')).toBe('true');
    expect(pillola.classList).toContain('attivo');
  });

  it('la provincia chiusa dice di cosa si parla', () => {
    crea();

    expect(pagina.testoProvincia()).toBe('Tutte le province');
    pagina.cambiaProvincia('LT');
    expect(pagina.testoProvincia()).toBe('Latina');
  });

  it('si combina con la ricerca per nome', () => {
    ricordaAmministratore(true);
    crea();

    pagina.cambiaSoloSenzaPosizione(true);
    pagina.cambiaFiltro('ostia');

    expect(nomi()).toEqual(['Virtus Ostia']);
  });

  it('non filtra più se chi amministra è uscito', () => {
    ricordaAmministratore(true);
    crea();
    pagina.cambiaSoloSenzaPosizione(true);

    ricordaAmministratore(false);
    pagina.ionViewWillEnter();
    fixture.detectChanges();

    expect(interruttore()).toBeNull();
    expect(nomi().length).toBe(CAMPI.length);
  });

  it('il filtro per provincia lo vede anche chi non amministra, con le province dei dati', () => {
    crea();

    expect(selettoreProvincia()).not.toBeNull();
    expect(pagina.opzioniProvincia().map((o) => o.etichetta)).toEqual([
      'Tutte',
      'Latina',
      'Roma',
      'Provincia sconosciuta',
    ]);
  });

  it('lascia solo i campi della provincia scelta', () => {
    crea();

    pagina.cambiaProvincia('LT');
    expect(nomi()).toEqual(['Latina Calcio']);

    pagina.cambiaProvincia('sconosciuta');
    expect(nomi()).toEqual(['Sconosciuta']);

    pagina.cambiaProvincia('tutte');
    expect(nomi().length).toBe(CAMPI.length);
    expect(pagina.filtrato()).toBeFalse();
  });

  it('provincia, ricerca e filtro di chi amministra si sommano', () => {
    ricordaAmministratore(true);
    crea();

    pagina.cambiaProvincia('RM');
    expect(pagina.filtrato()).toBeTrue();
    pagina.cambiaSoloSenzaPosizione(true);
    expect(nomi()).toEqual(['Virtus Ostia', 'Tor di Quinto']);

    pagina.cambiaFiltro('quinto');
    expect(nomi()).toEqual(['Tor di Quinto']);
  });

  it('parte dalla provincia scelta prima, anche sulla mappa', () => {
    TestBed.inject(ProvinciaSceltaService).scegli('LT');
    crea();

    expect(pagina.provincia()).toBe('LT');
    expect(nomi()).toEqual(['Latina Calcio']);
  });

  it('una provincia ricordata che non ha più campi non nasconde tutto', () => {
    TestBed.inject(ProvinciaSceltaService).scegli('VT');
    crea();

    expect(pagina.provincia()).toBe('tutte');
    expect(nomi().length).toBe(CAMPI.length);
  });

  describe('al rientro dopo un cambio di chi amministra', () => {
    it('ricarica senza spinner e tiene filtro e provincia', () => {
      crea();
      pagina.cambiaFiltro('calcio');
      pagina.cambiaProvincia('LT');
      expect(nomi()).toEqual(['Latina Calcio']);

      // Eliminata dalla scheda: il backend non la manda più.
      scaricati = CAMPI.filter((c) => c.id !== '5');
      TestBed.inject(CampiCambiatiService).segnala();
      const stati: string[] = [];
      const originale = pagina.stato.set.bind(pagina.stato);
      spyOn(pagina.stato, 'set').and.callFake((valore) => {
        stati.push(valore);
        originale(valore);
      });
      pagina.ionViewWillEnter();

      expect(stati).not.toContain('caricamento');
      expect(pagina.campi().length).toBe(CAMPI.length - 1);
      expect(nomi()).toEqual([]);
      expect(pagina.filtro()).toBe('calcio');
    });

    it('senza cambi non riscarica niente', () => {
      crea();
      const service = TestBed.inject(SocietaService);
      const tutti = spyOn(service, 'tutti').and.callThrough();

      pagina.ionViewWillEnter();

      expect(tutti).not.toHaveBeenCalled();
    });
  });
});
