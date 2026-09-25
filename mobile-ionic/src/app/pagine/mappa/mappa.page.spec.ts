import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { Observable, of, throwError } from 'rxjs';
import { Partita, PartitePerCampo } from '../../modelli/partita';
import { Societa } from '../../modelli/societa';
import { ricordaAmministratore } from '../../servizi/amministratore-ricordato';
import { NumeroViciniService } from '../../servizi/numero-vicini.service';
import { ErrorePosizione, PosizioneService, errorePerCodice } from '../../servizi/posizione.service';
import { SONDE_BROWSER } from '../../servizi/sonde-browser';
import { AGENTI, SondeFinte } from '../../servizi/sonde-finte.spec';
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
  campo('1', 'RM', { lat: 41.89, lng: 12.48, anagraficaImpiantoId: 190 }),
  campo('2', 'RM'),
  campo('3', 'LT', { lat: 41.47, lng: 12.9, anagraficaImpiantoId: 191 }),
  campo('4', 'FR', { lat: 41.64, lng: 13.35 }),
];

function partita(casa: string, dataOra = '2026-09-06T11:00:00+02:00'): Partita {
  return {
    dataOra,
    casa,
    ospite: 'OSPITE',
    campionato: 'ECCELLENZA',
    ente: 'Regionali',
    girone: 'A',
    giornata: 1,
  };
}

describe('MappaPage', () => {
  let fixture: ComponentFixture<MappaPage>;
  let pagina: MappaPage;
  let posizione: jasmine.Spy;
  /** Quello che risponde il backend per le partite: di default nessuna. */
  let partiteSuiCampi: Observable<PartitePerCampo>;

  function crea(): void {
    fixture = TestBed.createComponent(MappaPage);
    pagina = fixture.componentInstance;
    fixture.detectChanges();
  }

  function sullaMappa(): string[] {
    return pagina.geolocalizzati().map((societa) => societa.id);
  }

  let sonde: SondeFinte;

  beforeEach(() => {
    sonde = SondeFinte.androidChrome();
    partiteSuiCampi = of({});
    // Chi guarda sta a Roma: dal campo 1 poche centinaia di metri.
    posizione = jasmine.createSpy('attuale').and.resolveTo({ lat: 41.892, lng: 12.482 });
    TestBed.configureTestingModule({
      imports: [MappaPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        {
          provide: SocietaService,
          useValue: { tutti: () => of(CAMPI), partiteSuiCampi: () => partiteSuiCampi },
        },
        { provide: PosizioneService, useValue: { attuale: posizione } },
        { provide: SONDE_BROWSER, useValue: sonde },
      ],
    });
  });

  afterEach(() => {
    ricordaAmministratore(false);
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

  /**
   * "N campi sulla mappa · M ancora senza posizione" è un dato di lavoro per
   * chi amministra: al pubblico direbbe solo che mancano dei campi.
   */
  describe('il conto dei campi sopra la mappa', () => {
    function riepilogo(): string | null {
      const elemento = fixture.nativeElement.querySelector('.riepilogo') as HTMLElement | null;
      return elemento?.textContent?.replace(/\s+/g, ' ').trim() ?? null;
    }

    it('non si vede a chi non amministra', () => {
      ricordaAmministratore(false);
      crea();

      expect(riepilogo()).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('ancora senza posizione');
    });

    it('si vede a chi amministra, con il link ai campi da sistemare', () => {
      ricordaAmministratore(true);
      crea();

      expect(riepilogo()).toBe('3 campi sulla mappa · 1 ancora senza posizione');
      expect(fixture.nativeElement.querySelector('.riepilogo a').getAttribute('href')).toBe('/campi');
    });

    it('si adegua rientrando nella pagina dopo il login', () => {
      ricordaAmministratore(false);
      crea();
      ricordaAmministratore(true);

      pagina.ionViewWillEnter();
      fixture.detectChanges();
      expect(riepilogo()).toContain('ancora senza posizione');
    });
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

    function casoMostrato(): string | null {
      fixture.detectChanges();
      return (
        fixture.nativeElement
          .querySelector('riquadro-diagnosi.errore-vicini [data-caso]')
          ?.getAttribute('data-caso') ?? null
      );
    }

    for (const [codice, caso, parola] of [
      [1, 'rifiutata', 'Non hai dato il permesso'],
      [2, 'non-disponibile', 'accendi «Posizione»'],
      [3, 'scaduta', 'non è arrivata in tempo'],
    ] as const) {
      it(`con l errore ${codice} spiega come sbloccarla e lascia la mappa com era`, async () => {
        posizione.and.rejectWith(errorePerCodice(codice));
        crea();
        await vicinoAMe();

        expect(pagina.posizione()).toBeNull();
        expect(pagina.cercoPosizione()).toBeFalse();
        expect(casoMostrato()).toBe(caso);
        expect(testo()).toContain(parola);
        expect(fixture.nativeElement.querySelector('.errore-vicini[role=alert]')).not.toBeNull();
      });
    }

    it('bloccata per il sito: i passi di Chrome su Android', async () => {
      sonde.permessi = { geolocation: 'denied' };
      posizione.and.rejectWith(errorePerCodice(1));
      crea();
      await vicinoAMe();

      expect(casoMostrato()).toBe('bloccata');
      expect(testo()).toContain('Autorizzazioni → Posizione → Consenti');
    });

    it('su iPhone i passi di Safari', async () => {
      sonde.userAgent = AGENTI.iphoneSafari;
      sonde.permessi = null;
      posizione.and.rejectWith(errorePerCodice(1));
      crea();
      await vicinoAMe();

      expect(casoMostrato()).toBe('bloccata');
      expect(testo()).toContain('Siti web di Safari');
    });

    it('nel browser di Telegram dice di aprire la pagina in Chrome', async () => {
      sonde.userAgent = AGENTI.androidTelegram;
      posizione.and.rejectWith(errorePerCodice(1));
      crea();
      await vicinoAMe();

      expect(testo()).toContain('Telegram');
      expect(testo()).toContain('Apri in Chrome');
    });

    it('fuori da HTTPS lo dice', async () => {
      posizione.and.rejectWith(new ErrorePosizione('non-sicura', 'Serve HTTPS.'));
      crea();
      await vicinoAMe();

      expect(pagina.erroreVicini()).toBe('Serve HTTPS.');
      expect(casoMostrato()).toBe('non-sicura');
      expect(testo()).toContain('HTTPS');
    });

    it('un errore che la diagnosi non sa spiegare resta un messaggio semplice', async () => {
      sonde.permessi = { geolocation: 'granted' };
      posizione.and.rejectWith(errorePerCodice(1));
      crea();
      await vicinoAMe();

      // Negata ma ora consentita: niente da sbloccare, resta il messaggio.
      expect(casoMostrato()).toBeNull();
      expect(fixture.nativeElement.querySelector('p.errore-vicini[role=alert]')).not.toBeNull();
    });

    it('Riprova nel riquadro rifà la ricerca, Chiudi lo toglie', async () => {
      posizione.and.rejectWith(errorePerCodice(3));
      crea();
      await vicinoAMe();

      posizione.and.resolveTo({ lat: 41.47, lng: 12.9 });
      fixture.nativeElement.querySelector('riquadro-diagnosi .riprova').click();
      await new Promise((risolvi) => setTimeout(risolvi, 0));
      fixture.detectChanges();
      expect(casoMostrato()).toBeNull();
      expect(vicini()[0]).toBe('3');

      posizione.and.rejectWith(errorePerCodice(3));
      await vicinoAMe();
      fixture.nativeElement.querySelector('riquadro-diagnosi .chiudi').click();
      fixture.detectChanges();
      expect(casoMostrato()).toBeNull();
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

  describe('partite sui campi', () => {
    /** Solo il campo 3, a Latina, ha partite; il campo 1 non ne ha questa settimana. */
    beforeEach(() => {
      partiteSuiCampi = of({ '191': [partita('LATINA'), partita('<b>X</b>')] });
    });

    function popup(id: string): string {
      const campo = pagina['mostrati'].find((c) => c.id === id)!;
      return pagina['popup'](campo);
    }

    it('il popup mostra le prossime partite del campo, abbinate per id', async () => {
      crea();
      await disegnata();

      const testo = popup('3');
      expect(testo).toContain('dom 6 set, 11:00');
      expect(testo).toContain('LATINA – OSPITE');
      expect(testo).toContain('ECCELLENZA · Girone A · Regionali');
      // I nomi arrivano da fuori: nel popup non diventano HTML.
      expect(testo).toContain('&lt;b&gt;X&lt;/b&gt;');
      expect(popup('1')).not.toContain('partite-popup');
    });

    it('"Solo campi con partite" lascia solo quelli dove si gioca, insieme alla provincia', async () => {
      crea();
      await disegnata();
      expect(fixture.nativeElement.querySelector('.filtro-partite')).not.toBeNull();

      pagina.cambiaSoloConPartite(true);
      await disegnata();

      expect(sullaMappa()).toEqual(['3']);
      expect(pagina['mostrati'].map((s) => s.id)).toEqual(['3']);
      // Il conteggio dei campi senza posizione non cambia: non dipende dalle partite.
      expect(pagina.senzaPosizione()).toBe(1);

      pagina.cambiaProvincia('RM');
      expect(sullaMappa()).toEqual([]);

      pagina.cambiaSoloConPartite(false);
      expect(sullaMappa()).toEqual(['1']);
    });

    it('vale anche per "Vicino a me": i vicini sono cercati fra i campi con partite', async () => {
      crea();
      pagina.cambiaSoloConPartite(true);
      await pagina.vicinoAMe();
      await disegnata();

      expect(pagina.vicini().map((v) => v.campo.id)).toEqual(['3']);
    });

    it('senza partite, o se non arrivano, niente interruttore e la mappa resta intera', async () => {
      partiteSuiCampi = throwError(() => new Error('502'));
      crea();
      await disegnata();

      expect(pagina.ciSonoPartite()).toBeFalse();
      expect(fixture.nativeElement.querySelector('.filtro-partite')).toBeNull();
      pagina.cambiaSoloConPartite(true);
      expect(sullaMappa()).toEqual(['1', '3', '4']);
      expect(popup('3')).not.toContain('partite-popup');
    });
  });

  /**
   * "Partenza" e "Percorso" nel popup: HTML scritto a mano, ricollegato
   * all'apertura come "Vedi scheda società".
   */
  describe('percorso nel popup', () => {
    /** Il popup del campo 1 com'è nel DOM dopo l'apertura, con gli eventi collegati. */
    function apriPopup(): HTMLFormElement {
      const campo = pagina['mostrati'].find((c) => c.id === '1')!;
      const contenitore = document.createElement('div');
      contenitore.innerHTML = pagina['popup'](campo);
      const modulo = contenitore.querySelector<HTMLFormElement>('[data-percorso]')!;
      pagina['collegaPercorso'](modulo);
      return modulo;
    }

    function link(modulo: HTMLFormElement): HTMLAnchorElement {
      return modulo.querySelector<HTMLAnchorElement>('.apri-percorso')!;
    }

    it('aprire il popup non chiede la posizione, e il link lascia partire Google dal dispositivo', async () => {
      crea();
      await disegnata();
      const modulo = apriPopup();

      expect(posizione).not.toHaveBeenCalled();
      expect(modulo.querySelector('input')!.placeholder).toBe('La mia posizione');
      expect(link(modulo).href).toBe('https://www.google.com/maps/dir/?api=1&destination=41.89,12.48');
      expect(link(modulo).target).toBe('_blank');
      expect(link(modulo).rel).toBe('noopener');
    });

    it("l'indirizzo scritto diventa la partenza", async () => {
      crea();
      await disegnata();
      const modulo = apriPopup();
      const campo = modulo.querySelector('input')!;

      campo.value = 'Piazza Venezia, Roma';
      campo.dispatchEvent(new Event('input'));

      expect(link(modulo).href).toBe(
        'https://www.google.com/maps/dir/?api=1&origin=Piazza%20Venezia%2C%20Roma&destination=41.89,12.48',
      );
    });

    it('il pulsante chiede la posizione e parte da lì, anche nei popup aperti dopo', async () => {
      crea();
      await disegnata();
      const modulo = apriPopup();

      modulo.querySelector<HTMLButtonElement>('[data-usa-posizione]')!.click();
      await disegnata();

      expect(posizione).toHaveBeenCalledTimes(1);
      expect(link(modulo).href).toContain('&origin=41.892,12.482&');
      expect(modulo.querySelector('.nota-percorso')!.textContent).toContain('tua posizione attuale');
      expect(link(apriPopup()).href).toContain('&origin=41.892,12.482&');
      // L'errore del percorso non è quello di "Vicino a me".
      expect(pagina.erroreVicini()).toBeNull();
    });

    it('se la posizione non arriva lo dice nel popup, e il link resta senza partenza', async () => {
      posizione.and.rejectWith(errorePerCodice(1));
      crea();
      await disegnata();
      const modulo = apriPopup();

      modulo.querySelector<HTMLButtonElement>('[data-usa-posizione]')!.click();
      await disegnata();

      const nota = modulo.querySelector('.nota-percorso')!;
      expect(nota.classList).toContain('errore');
      expect(nota.textContent).toContain('Non hai dato il permesso');
      expect(link(modulo).href).not.toContain('origin');
      expect(pagina.riquadroVicini()).toBeNull();
    });

    it('con "Vicino a me" già chiesto parte dalla posizione avuta', async () => {
      crea();
      await pagina.vicinoAMe();
      await disegnata();

      expect(link(apriPopup()).href).toContain('&origin=41.892,12.482&');
    });

    it('i nomi che arrivano da fuori non diventano HTML nel popup', async () => {
      crea();
      await disegnata();
      const campo = { ...pagina['mostrati'][0], nomeImpianto: '<img src=x onerror=alert(1)>' };

      expect(pagina['popup'](campo)).not.toContain('<img');
    });
  });

  /** L'effetto disegna dopo che l'icona per il canvas è caricata: si aspetta anche quella. */
  async function disegnata(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((fatto) => setTimeout(fatto, 50));
  }
});
