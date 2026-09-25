import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { AmbientePush } from '../../servizi/ambiente-push';
import { AmbientePushFinto } from '../../servizi/ambiente-push-finto.spec';
import { PosizioneService, errorePerCodice } from '../../servizi/posizione.service';
import { SONDE_BROWSER } from '../../servizi/sonde-browser';
import { SondeFinte } from '../../servizi/sonde-finte.spec';
import { PercorsoCampoComponent } from './percorso-campo.component';

/** "Partenza" e "Percorso" sotto la mappa della scheda. */
describe('PercorsoCampoComponent', () => {
  let fixture: ComponentFixture<PercorsoCampoComponent>;
  let posizione: jasmine.Spy;
  let sonde: SondeFinte;

  function crea(): HTMLElement {
    TestBed.configureTestingModule({
      imports: [PercorsoCampoComponent],
      providers: [
        provideIonicAngular(),
        { provide: PosizioneService, useValue: { attuale: posizione } },
        { provide: SONDE_BROWSER, useValue: sonde },
        { provide: AmbientePush, useValue: new AmbientePushFinto() },
      ],
    });
    fixture = TestBed.createComponent(PercorsoCampoComponent);
    fixture.componentRef.setInput('destinazione', { lat: 41.9352, lng: 12.4561 });
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  async function aggiornata(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function link(elemento: HTMLElement): HTMLAnchorElement {
    return elemento.querySelector<HTMLAnchorElement>('a.apri-percorso')!;
  }

  beforeEach(() => {
    sonde = SondeFinte.androidChrome();
    posizione = jasmine.createSpy('attuale').and.resolveTo({ lat: 41.892, lng: 12.482 });
  });

  afterEach(() => fixture?.destroy());

  it('parte vuota, "La mia posizione", senza chiedere niente: Google parte dal dispositivo', () => {
    const elemento = crea();

    expect(posizione).not.toHaveBeenCalled();
    expect(elemento.querySelector('input')!.placeholder).toBe('La mia posizione');
    expect(link(elemento).href).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=41.9352,12.4561',
    );
    expect(link(elemento).target).toBe('_blank');
    expect(link(elemento).rel).toBe('noopener');
  });

  it("l'indirizzo scritto diventa la partenza", () => {
    const elemento = crea();
    const campo = elemento.querySelector('input')!;

    campo.value = 'Via Tuscolana 10, Roma';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(link(elemento).href).toContain('&origin=Via%20Tuscolana%2010%2C%20Roma&');
  });

  it('il pulsante svuota l indirizzo, chiede la posizione e parte da lì', async () => {
    const elemento = crea();
    fixture.componentInstance.scrivi('Latina');

    await fixture.componentInstance.usaPosizione();
    await aggiornata();

    expect(posizione).toHaveBeenCalledTimes(1);
    expect(elemento.querySelector('input')!.value).toBe('');
    expect(link(elemento).href).toContain('&origin=41.892,12.482&');
    expect(elemento.textContent).toContain('Parti dalla tua posizione attuale.');
  });

  it('se la posizione è negata spiega come sbloccarla, e il link resta usabile', async () => {
    posizione.and.rejectWith(errorePerCodice(1));
    const elemento = crea();

    await fixture.componentInstance.usaPosizione();
    await aggiornata();

    expect(elemento.querySelector('riquadro-diagnosi')).not.toBeNull();
    expect(link(elemento).href).not.toContain('origin');
  });

  it('dove la posizione non arriva mai chiede di scrivere la partenza', () => {
    sonde.sicuro = false;
    const elemento = crea();

    expect(elemento.querySelector('input')!.placeholder).toBe('Scrivi da dove parti');
  });
});
