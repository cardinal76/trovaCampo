import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { Societa } from '../../modelli/societa';
import { ricordaAmministratore } from '../../servizi/amministratore-ricordato';
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
];

describe('ElencoPage', () => {
  let fixture: ComponentFixture<ElencoPage>;
  let pagina: ElencoPage;

  function crea(): void {
    fixture = TestBed.createComponent(ElencoPage);
    pagina = fixture.componentInstance;
    fixture.detectChanges();
  }

  function interruttore(): HTMLElement | null {
    return fixture.nativeElement.querySelector('ion-toggle.filtro-posizione');
  }

  function nomi(): string[] {
    return pagina.filtrati().map((societa) => societa.nomeSocieta);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ElencoPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        { provide: SocietaService, useValue: { tutti: () => of(CAMPI) } },
      ],
    });
  });

  afterEach(() => ricordaAmministratore(false));

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

    expect(nomi()).toEqual(['Virtus Ostia', 'Tor di Quinto']);

    pagina.cambiaSoloSenzaPosizione(false);

    expect(nomi().length).toBe(CAMPI.length);
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
});
