import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { environment } from '../../../environments/environment';
import { ElencoProssime, ProssimaPartita } from '../../modelli/prossime';
import { AmbientePush } from '../../servizi/ambiente-push';
import { AmbientePushFinto, attendi } from '../../servizi/ambiente-push-finto.spec';
import { PosizioneService } from '../../servizi/posizione.service';
import { SONDE_BROWSER } from '../../servizi/sonde-browser';
import { SondeFinte } from '../../servizi/sonde-finte.spec';
import { ProssimePartitePage } from './prossime-partite.page';

const API = `${environment.apiUrl}/api/notifiche/prossime`;

const LODIGIANI = {
  chiave: '412|eccellenza|regionali|',
  societa: 'LODIGIANI CALCIO 1972',
  societaId: 'campo-lodigiani',
  campionato: 'ECCELLENZA',
  dettaglio: 'Girone B · Regionali',
};

/** Domani alle 15, nel fuso di chi esegue i test: basta che sia domani. */
function domaniAlle(ora: number): string {
  const domani = new Date();
  domani.setDate(domani.getDate() + 1);
  domani.setHours(ora, 0, 0, 0);
  return domani.toISOString();
}

function partita(parziale: Partial<ProssimaPartita> = {}): ProssimaPartita {
  return {
    dataOra: domaniAlle(15),
    casa: 'LODIGIANI CALCIO 1972',
    ospite: 'VIS SEZZE',
    campionato: 'ECCELLENZA',
    girone: 'B',
    ente: 'Regionali',
    giornata: 3,
    campo: {
      id: 'campo-lodigiani',
      nomeImpianto: 'La Borghesiana',
      indirizzo: 'Via di Vermicino 105',
      localita: 'Roma',
    },
    avvisi: [
      {
        tipo: 'squadra',
        seguite: ['LODIGIANI CALCIO 1972'],
        distanzaKm: null,
        alle: domaniAlle(14),
        stato: 'previsto',
      },
    ],
    ...parziale,
  };
}

describe('ProssimePartitePage', () => {
  let fixture: ComponentFixture<ProssimePartitePage>;
  let ambiente: AmbientePushFinto;
  let http: HttpTestingController;

  afterEach(() => {
    http.verify();
    localStorage.removeItem('trovacampo.notifiche');
    localStorage.removeItem('trovacampo.squadreSeguite');
  });

  function apri(prepara: (a: AmbientePushFinto) => void = () => undefined): HTMLElement {
    ambiente = new AmbientePushFinto();
    prepara(ambiente);
    TestBed.configureTestingModule({
      imports: [ProssimePartitePage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AmbientePush, useValue: ambiente },
        { provide: PosizioneService, useValue: { attuale: jasmine.createSpy('attuale') } },
        { provide: SONDE_BROWSER, useValue: new SondeFinte() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProssimePartitePage);
    fixture.componentInstance.ionViewWillEnter();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  async function rispondi(elenco: Partial<ElencoProssime>): Promise<void> {
    await attendi();
    http.expectOne(API).flush({ fino: '2026-09-12', partite: [], altre: false, ...elenco });
    await attendi();
    fixture.detectChanges();
  }

  function testo(pagina: HTMLElement, selettore: string): string[] {
    return Array.from(pagina.querySelectorAll(selettore)).map(
      (elemento) => elemento.textContent?.replace(/\s+/g, ' ').trim() ?? '',
    );
  }

  it('senza squadre seguite né partite vicine spiega cosa fare e porta a Notifiche', async () => {
    const pagina = apri();
    await attendi();

    expect(pagina.querySelector('[data-vuota=scelte]')).not.toBeNull();
    expect(pagina.querySelector('[data-vuota=scelte] ion-button')?.getAttribute('routerLink')).toBe(
      '/notifiche',
    );
    // Niente da chiedere al server.
    http.expectNone(API);
  });

  it('elenca le partite per giorno, con motivo, ora dell avviso e campo', async () => {
    localStorage.setItem('trovacampo.squadreSeguite', JSON.stringify([LODIGIANI]));
    localStorage.setItem('trovacampo.notifiche', JSON.stringify({ avvisoSquadre: true }));
    const pagina = apri((a) => (a.permessoAttuale = 'granted'));

    await rispondi({
      partite: [
        partita(),
        partita({
          dataOra: domaniAlle(17),
          casa: 'BOREALE',
          campo: null,
          avvisi: [
            {
              tipo: 'squadra',
              seguite: ['VIS SEZZE'],
              distanzaKm: null,
              alle: domaniAlle(16),
              stato: 'inviato',
            },
          ],
        }),
      ],
    });

    expect(testo(pagina, '.titolo-giorno .vicino')).toEqual(['Domani']);
    expect(testo(pagina, '.squadre')).toEqual([
      'LODIGIANI CALCIO 1972 – VIS SEZZE',
      'BOREALE – VIS SEZZE',
    ]);
    expect(testo(pagina, '.motivo')).toEqual(['Segui LODIGIANI CALCIO 1972', 'Segui VIS SEZZE']);
    expect(testo(pagina, '.quando')[0]).toMatch(/^Avviso alle \d\d:\d\d$/);
    expect(testo(pagina, '.quando')[1]).toMatch(/^Avviso inviato alle \d\d:\d\d$/);
    expect(testo(pagina, '.campo a')).toEqual(['La Borghesiana · Via di Vermicino 105, Roma']);
    expect(pagina.querySelector('.campo a')?.getAttribute('href')).toBe('/societa/campo-lodigiani');
    expect(testo(pagina, '.campo span')).toEqual(['Campo non ancora su TrovaCampo']);
    expect(pagina.querySelector('[data-avviso=spente]')).toBeNull();
  });

  it('con le notifiche spente mostra lo stesso le partite delle squadre seguite, e lo dice', async () => {
    localStorage.setItem('trovacampo.squadreSeguite', JSON.stringify([LODIGIANI]));
    const pagina = apri();

    await rispondi({ partite: [partita()] });

    expect(pagina.querySelector('[data-avviso=spente]')).not.toBeNull();
    expect(testo(pagina, '.quando')).toEqual(['Avviso spento']);
    expect(pagina.querySelector('.avviso-partita')?.getAttribute('data-tono')).toBe('spento');
  });

  it('con gli avvisi accesi ma nessuna partita lo dice', async () => {
    localStorage.setItem(
      'trovacampo.notifiche',
      JSON.stringify({
        avvisoVicino: true,
        raggioKm: 20,
        posizione: { lat: 41.9, lng: 12.5, il: '2026-09-05T10:00:00Z' },
      }),
    );
    const pagina = apri((a) => (a.permessoAttuale = 'granted'));

    await rispondi({ partite: [] });

    expect(pagina.querySelector('[data-vuota=partite]')).not.toBeNull();
    expect(testo(pagina, '[data-vuota=partite] p')[0]).toContain('sui campi entro 20 km');
  });

  it('se il server non risponde lo dice e si può riprovare', async () => {
    localStorage.setItem('trovacampo.squadreSeguite', JSON.stringify([LODIGIANI]));
    const pagina = apri();
    await attendi();
    http.expectOne(API).flush(null, { status: 503, statusText: 'Non disponibile' });
    await attendi();
    fixture.detectChanges();

    expect(pagina.querySelector('[data-vuota=errore]')).not.toBeNull();
    (pagina.querySelector('[data-vuota=errore] ion-button') as HTMLElement).click();
    await rispondi({ partite: [partita()] });
    expect(pagina.querySelector('[data-vuota=errore]')).toBeNull();
    expect(pagina.querySelectorAll('.partita').length).toBe(1);
  });
});
