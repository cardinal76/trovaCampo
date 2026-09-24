import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { Societa } from '../../modelli/societa';
import { ProvinciaSceltaService } from '../../servizi/provincia-scelta.service';
import { SocietaService } from '../../servizi/societa.service';
import { MappaPage } from './mappa.page';

function campo(id: string, provinciaImpianto: string, valori: Partial<Societa> = {}): Societa {
  return {
    id,
    siglaSocieta: '',
    nomeSocieta: `Società ${id}`,
    comitatoRegionale: '',
    nomeImpianto: `Campo ${id}`,
    indirizzoImpianto: 'Via Roma 1',
    localitaImpianto: '',
    provinciaImpianto,
    ...valori,
  };
}

const CAMPI: Societa[] = [
  campo('1', 'RM', { lat: 41.89, lng: 12.48 }),
  campo('2', 'RM'),
  campo('3', 'LT', { lat: 41.47, lng: 12.9 }),
  campo('4', 'FR', { lat: 41.64, lng: 13.35 }),
];

describe('MappaPage', () => {
  let fixture: ComponentFixture<MappaPage>;
  let pagina: MappaPage;

  function crea(): void {
    fixture = TestBed.createComponent(MappaPage);
    pagina = fixture.componentInstance;
    fixture.detectChanges();
  }

  function sullaMappa(): string[] {
    return pagina.geolocalizzati().map((societa) => societa.id);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MappaPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        { provide: SocietaService, useValue: { tutti: () => of(CAMPI) } },
      ],
    });
  });

  afterEach(() => {
    fixture?.destroy();
    localStorage.removeItem('trovacampo.provincia');
  });

  it('mostra il filtro per provincia a tutti, con le province dei dati', () => {
    crea();

    expect(fixture.nativeElement.querySelector('ion-select.filtro-provincia')).not.toBeNull();
    expect(pagina.opzioniProvincia().map((o) => o.etichetta)).toEqual([
      'Tutte',
      'Frosinone',
      'Latina',
      'Roma',
    ]);
  });

  it('senza filtro mette sulla mappa tutti i campi con una posizione', () => {
    crea();

    expect(sullaMappa()).toEqual(['1', '3', '4']);
    expect(pagina.senzaPosizione()).toBe(1);
  });

  it('con una provincia lascia solo i suoi segnaposto, e conta solo i suoi senza posizione', () => {
    crea();

    pagina.cambiaProvincia('LT');

    expect(sullaMappa()).toEqual(['3']);
    expect(pagina.senzaPosizione()).toBe(0);

    pagina.cambiaProvincia('RM');

    expect(sullaMappa()).toEqual(['1']);
    expect(pagina.senzaPosizione()).toBe(1);
  });

  it('la scelta è la stessa dell elenco', () => {
    TestBed.inject(ProvinciaSceltaService).scegli('FR');
    crea();

    expect(pagina.provincia()).toBe('FR');
    expect(sullaMappa()).toEqual(['4']);

    pagina.cambiaProvincia('tutte');

    expect(TestBed.inject(ProvinciaSceltaService).scelta()).toBe('tutte');
  });

  it('cambiando provincia la mappa resta la stessa, con i soli segnaposto rimasti', async () => {
    crea();
    await disegnata();
    const mappa = pagina['mappa'];
    expect(mappa).not.toBeNull();
    expect(pagina['mostrati'].map((societa) => societa.id)).toEqual(['1', '3', '4']);

    pagina.cambiaProvincia('LT');
    await disegnata();

    expect(pagina['mappa']).toBe(mappa);
    expect(pagina['mostrati'].map((societa) => societa.id)).toEqual(['3']);
    // La vista si è spostata sull'unico campo rimasto.
    expect(mappa!.getCenter().distanceTo([41.47, 12.9])).toBeLessThan(1000);
  });

  /** L'effetto disegna dopo che l'icona per il canvas è caricata: si aspetta anche quella. */
  async function disegnata(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((fatto) => setTimeout(fatto, 50));
  }
});
