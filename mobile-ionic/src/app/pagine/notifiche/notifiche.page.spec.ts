import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { environment } from '../../../environments/environment';
import { AmbientePush } from '../../servizi/ambiente-push';
import { AmbientePushFinto, attendi } from '../../servizi/ambiente-push-finto.spec';
import { NotificheService } from '../../servizi/notifiche.service';
import { PosizioneService } from '../../servizi/posizione.service';
import { NotifichePage, quandoSalvata } from './notifiche.page';

describe('NotifichePage', () => {
  let fixture: ComponentFixture<NotifichePage>;
  let ambiente: AmbientePushFinto;
  let http: HttpTestingController;

  afterEach(() => {
    localStorage.removeItem('trovacampo.notifiche');
    localStorage.removeItem('trovacampo.squadreSeguite');
  });

  function apri(prepara: (a: AmbientePushFinto) => void = () => undefined): HTMLElement {
    ambiente = new AmbientePushFinto();
    prepara(ambiente);
    TestBed.configureTestingModule({
      imports: [NotifichePage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AmbientePush, useValue: ambiente },
        { provide: PosizioneService, useValue: { attuale: async () => ({ lat: 41.9, lng: 12.5 }) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NotifichePage);
    fixture.componentInstance.ionViewWillEnter();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function interruttore(pagina: HTMLElement, avviso: string): HTMLIonToggleElement {
    return pagina.querySelector(`ion-toggle[data-avviso=${avviso}]`) as HTMLIonToggleElement;
  }

  it('parte con i due avvisi spenti, e spiega come seguire una squadra', () => {
    const pagina = apri();

    expect(interruttore(pagina, 'squadre').checked).toBeFalse();
    expect(interruttore(pagina, 'vicino').checked).toBeFalse();
    expect(interruttore(pagina, 'squadre').disabled).toBeFalse();
    expect(pagina.querySelector('.seguite .vuoto')?.textContent).toContain('campanella');
    expect(pagina.textContent).toContain('non può seguire la tua posizione in sottofondo');
    expect(pagina.querySelector('.avviso')).toBeNull();
  });

  it('su iPhone nel browser spiega come aggiungerla alla Home, con gli interruttori spenti', () => {
    const pagina = apri((a) => (a.supportoAttuale = 'iphone-da-installare'));

    expect(pagina.querySelector('[data-stato=iphone]')?.textContent).toContain(
      'Aggiungi alla schermata Home',
    );
    expect(interruttore(pagina, 'squadre').disabled).toBeTrue();
    expect(interruttore(pagina, 'vicino').disabled).toBeTrue();
  });

  it('su un browser senza push lo dice', () => {
    const pagina = apri((a) => (a.supportoAttuale = 'non-supportato'));

    expect(pagina.querySelector('[data-stato=non-supportato]')).not.toBeNull();
  });

  it('con le notifiche bloccate dice dove riattivarle', () => {
    const pagina = apri((a) => (a.permessoAttuale = 'denied'));

    expect(pagina.querySelector('[data-stato=bloccate]')?.textContent).toContain('bloccate');
  });

  it('elenca le squadre seguite, e si smette di seguirle', async () => {
    localStorage.setItem(
      'trovacampo.squadreSeguite',
      JSON.stringify([
        {
          chiave: '412|eccellenza|regionali|',
          societa: 'LODIGIANI',
          societaId: 'abc',
          campionato: 'ECCELLENZA',
          dettaglio: 'Girone B',
        },
      ]),
    );
    const pagina = apri();

    const seguita = pagina.querySelector('.seguita')!;
    expect(seguita.textContent).toContain('LODIGIANI');
    expect(seguita.textContent).toContain('ECCELLENZA');
    expect(seguita.querySelector('a')?.getAttribute('href')).toBe('/societa/abc');

    (seguita.querySelector('ion-button.smetti') as HTMLElement).click();
    await attendi();
    fixture.detectChanges();
    expect(pagina.querySelector('.seguita')).toBeNull();
  });

  it('se il permesso viene negato l interruttore torna spento e compare il motivo', async () => {
    const pagina = apri((a) => (a.risposta = 'denied'));
    const toggle = interruttore(pagina, 'squadre');
    toggle.checked = true;

    await fixture.componentInstance.cambiaSquadre({
      target: toggle,
      detail: { checked: true },
    } as unknown as CustomEvent<{ checked: boolean }>);
    fixture.detectChanges();

    expect(toggle.checked).toBeFalse();
    expect(pagina.querySelector('.avviso.errore')?.textContent).toContain('bloccate');
  });

  it('accendendo le vicine mostra quando è stata salvata la posizione', async () => {
    const pagina = apri();
    const toggle = interruttore(pagina, 'vicino');

    const fatto = fixture.componentInstance.cambiaVicino({
      target: toggle,
      detail: { checked: true },
    } as unknown as CustomEvent<{ checked: boolean }>);
    await attendi();
    http.expectOne(`${environment.apiUrl}/api/notifiche/chiave`).flush({ chiave: 'BChiave' });
    await attendi();
    http.expectOne((r) => r.method === 'PUT').flush(null);
    await fatto;
    fixture.detectChanges();

    expect(TestBed.inject(NotificheService).preferenze().avvisoVicino).toBeTrue();
    expect(pagina.querySelector('.stato-posizione')?.textContent).toContain('Posizione salvata oggi');
    expect(pagina.querySelector('.aggiorna-posizione')).not.toBeNull();
  });

  it('dice quando è stata salvata la posizione', () => {
    const adesso = new Date(2026, 8, 24, 18, 0);

    expect(quandoSalvata(new Date(2026, 8, 24, 14, 32).toISOString(), adesso)).toBe('oggi alle 14:32');
    expect(quandoSalvata(new Date(2026, 8, 23, 9, 5).toISOString(), adesso)).toBe('ieri alle 9:05');
    expect(quandoSalvata(new Date(2026, 8, 20, 18, 0).toISOString(), adesso)).toBe(
      'il 20 settembre alle 18:00',
    );
    expect(quandoSalvata('non una data', adesso)).toBe('');
  });
});
