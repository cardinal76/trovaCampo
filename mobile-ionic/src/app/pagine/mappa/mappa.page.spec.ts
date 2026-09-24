import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { Societa } from '../../modelli/societa';
import { NumeroViciniService } from '../../servizi/numero-vicini.service';
import { ErrorePosizione, PosizioneService, errorePerCodice } from '../../servizi/posizione.service';
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
  let posizione: jasmine.Spy;

  function crea(): void {
    fixture = TestBed.createComponent(MappaPage);
    pagina = fixture.componentInstance;
    fixture.detectChanges();
  }

  function sullaMappa(): string[] {
    return pagina.geolocalizzati().map((societa) => societa.id);
  }

  beforeEach(() => {
    // Chi guarda sta a Roma: dal campo 1 poche centinaia di metri.
    posizione = jasmine.createSpy('attuale').and.resolveTo({ lat: 41.892, lng: 12.482 });
    TestBed.configureTestingModule({
      imports: [MappaPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        { provide: SocietaService, useValue: { tutti: () => of(CAMPI) } },
        { provide: PosizioneService, useValue: { attuale: posizione } },
      ],
    });
  });

  afterEach(() => {
    fixture?.destroy();
    localStorage.removeItem('trovacampo.provincia');
    localStorage.removeItem('trovacampo.numeroVicini');
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

  describe('vicino a me', () => {
    /**
     * Come il tocco sul bottone, aspettando anche il disegno: Leaflet finisce
     * di ridisegnare il canvas nel fotogramma dopo, e la mappa deve esserci ancora.
     */
    async function vicinoAMe(): Promise<void> {
      await pagina.vicinoAMe();
      await disegnata();
    }

    function vicini(): string[] {
      return pagina.vicini().map((v) => v.campo.id);
    }

    function testo(): string {
      fixture.detectChanges();
      return (fixture.nativeElement as HTMLElement).textContent ?? '';
    }

    it('mostra i 3 campi più vicini, dal più vicino, con la distanza', async () => {
      crea();
      await vicinoAMe();
      await disegnata();

      expect(vicini()).toEqual(['1', '3', '4']);
      expect(pagina['mostrati'].map((s) => s.id)).toEqual(['1', '3', '4']);
      const righe = fixture.nativeElement.querySelectorAll('.elenco-vicini li');
      expect(righe.length).toBe(3);
      expect(righe[0].textContent).toContain('Società 1');
      expect(righe[0].querySelector('.distanza').textContent.trim()).toBe('280 m');
      expect(righe[0].querySelector('a').getAttribute('href')).toBe('/societa/1');
      expect(testo()).toContain('fra quelli di tutte le province');
    });

    it('il numero scelto cambia i campi mostrati ed è ricordato', async () => {
      crea();
      await vicinoAMe();
      pagina.cambiaNumeroVicini(1);
      await disegnata();

      expect(vicini()).toEqual(['1']);
      expect(pagina['mostrati'].map((s) => s.id)).toEqual(['1']);
      expect(localStorage.getItem('trovacampo.numeroVicini')).toBe('1');
      expect(TestBed.inject(NumeroViciniService).numero()).toBe(1);
    });

    it('cerca in tutte le province anche con un filtro scelto', async () => {
      TestBed.inject(ProvinciaSceltaService).scegli('FR');
      crea();
      await vicinoAMe();

      expect(vicini()).toEqual(['1', '3', '4']);
      // La provincia scelta resta quella, per quando si torna alla mappa.
      expect(pagina.provincia()).toBe('FR');
    });

    it('la vista comprende chi guarda e i campi vicini', async () => {
      crea();
      await disegnata();
      pagina.cambiaNumeroVicini(1);
      await vicinoAMe();
      await disegnata();

      const vista = pagina['mappa']!.getBounds();
      expect(vista.contains([41.892, 12.482])).toBeTrue();
      expect(vista.contains([41.89, 12.48])).toBeTrue();
      expect(pagina['livelloVicini']).not.toBeNull();
    });

    it('"Tutti i campi" torna alla vista della provincia', async () => {
      crea();
      await vicinoAMe();
      pagina.tornaAllaMappa();
      await disegnata();

      expect(pagina.posizione()).toBeNull();
      expect(pagina['mostrati'].map((s) => s.id)).toEqual(['1', '3', '4']);
      expect(pagina['livelloVicini']).toBeNull();
      expect(fixture.nativeElement.querySelector('.elenco-vicini')).toBeNull();
    });

    it('scegliere una provincia chiude la ricerca dei vicini', async () => {
      crea();
      await vicinoAMe();
      pagina.cambiaProvincia('LT');
      await disegnata();

      expect(pagina.posizione()).toBeNull();
      expect(sullaMappa()).toEqual(['3']);
    });

    for (const [codice, parola] of [
      [1, 'permesso'],
      [2, 'localizzazione sia attiva'],
      [3, 'in tempo'],
    ] as const) {
      it(`con l errore ${codice} lo spiega e lascia la mappa com era`, async () => {
        posizione.and.rejectWith(errorePerCodice(codice));
        crea();
        await vicinoAMe();

        expect(pagina.posizione()).toBeNull();
        expect(pagina.cercoPosizione()).toBeFalse();
        expect(testo()).toContain(parola);
        expect(fixture.nativeElement.querySelector('.errore-vicini[role=alert]')).not.toBeNull();
      });
    }

    it('senza geolocalizzazione o fuori da HTTPS mostra il messaggio del servizio', async () => {
      posizione.and.rejectWith(new ErrorePosizione('non-sicura', 'Serve HTTPS.'));
      crea();
      await vicinoAMe();

      expect(pagina.erroreVicini()).toBe('Serve HTTPS.');
      expect(testo()).toContain('Serve HTTPS.');
    });

    it('riprovando dopo un errore il messaggio sparisce', async () => {
      posizione.and.rejectWith(errorePerCodice(1));
      crea();
      await vicinoAMe();
      posizione.and.resolveTo({ lat: 41.47, lng: 12.9 });
      await vicinoAMe();

      expect(pagina.erroreVicini()).toBeNull();
      expect(vicini()[0]).toBe('3');
    });
  });

  /** L'effetto disegna dopo che l'icona per il canvas è caricata: si aspetta anche quella. */
  async function disegnata(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((fatto) => setTimeout(fatto, 50));
  }
});
