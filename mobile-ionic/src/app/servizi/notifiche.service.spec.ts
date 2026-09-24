import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { AmbientePush } from './ambiente-push';
import { AmbientePushFinto, ISCRIZIONE_FINTA, attendi } from './ambiente-push-finto.spec';
import { DiagnosiService } from './diagnosi.service';
import { NotificheService } from './notifiche.service';
import { ErrorePosizione, PosizioneService, errorePerCodice } from './posizione.service';
import { SONDE_BROWSER } from './sonde-browser';
import { SondeFinte } from './sonde-finte.spec';
import { SquadraSeguita } from './squadre-seguite.service';

const API = `${environment.apiUrl}/api/notifiche`;
const CHIAVE_VAPID = 'BChiaveVapidDelBackend';

const LODIGIANI: SquadraSeguita = {
  chiave: '412|eccellenza|regionali|',
  societa: 'LODIGIANI CALCIO 1972',
  societaId: 'abc',
  campionato: 'ECCELLENZA',
  dettaglio: 'Girone B · Regionali',
};

describe('NotificheService', () => {
  let ambiente: AmbientePushFinto;
  let gps: jasmine.SpyObj<PosizioneService>;
  let http: HttpTestingController;

  beforeEach(() => {
    ambiente = new AmbientePushFinto();
    gps = jasmine.createSpyObj<PosizioneService>('PosizioneService', ['attuale']);
    gps.attuale.and.resolveTo({ lat: 41.9, lng: 12.5 });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AmbientePush, useValue: ambiente },
        { provide: PosizioneService, useValue: gps },
        { provide: SONDE_BROWSER, useValue: new SondeFinte() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.removeItem('trovacampo.notifiche');
    localStorage.removeItem('trovacampo.squadreSeguite');
  });

  function servizio(): NotificheService {
    return TestBed.inject(NotificheService);
  }

  /** Risponde alla chiave VAPID e restituisce la PUT dell'iscrizione, ancora aperta. */
  async function iscrizioneMandata() {
    await attendi();
    http.expectOne(`${API}/chiave`).flush({ chiave: CHIAVE_VAPID });
    await attendi();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === `${API}/iscrizione`);
    return put;
  }

  it('parte con tutti e due gli avvisi spenti', () => {
    expect(servizio().preferenze()).toEqual({
      avvisoSquadre: false,
      avvisoVicino: false,
      raggioKm: 10,
      posizione: null,
    });
  });

  it('accendendo le squadre chiede il permesso, si iscrive e manda le squadre seguite', async () => {
    const notifiche = servizio();
    await notifiche.segui(LODIGIANI);

    const esito = notifiche.impostaAvvisoSquadre(true);
    const put = await iscrizioneMandata();
    expect(put.request.body).toEqual({
      ...ISCRIZIONE_FINTA,
      squadre: ['412|eccellenza|regionali|'],
      avvisoSquadre: true,
      avvisoVicino: false,
      posizione: null,
      raggioKm: 10,
    });
    put.flush(null, { status: 204, statusText: 'No Content' });

    expect(await esito).toBe('fatto');
    expect(ambiente.chiediPermesso).toHaveBeenCalled();
    expect(ambiente.chiaveUsata).toBe(CHIAVE_VAPID);
    expect(notifiche.preferenze().avvisoSquadre).toBeTrue();
    expect(JSON.parse(localStorage.getItem('trovacampo.notifiche')!).avvisoSquadre).toBeTrue();
  });

  function diagnosi(): DiagnosiService {
    return TestBed.inject(DiagnosiService);
  }

  it('con il permesso negato non accende niente, e la diagnosi dice bloccate', async () => {
    ambiente.risposta = 'denied';
    const notifiche = servizio();

    expect(await notifiche.impostaAvvisoSquadre(true)).toBe('permesso-negato');
    expect(notifiche.preferenze().avvisoSquadre).toBeFalse();
    expect(notifiche.permesso()).toBe('denied');
    expect(diagnosi().notifiche()).toBe('bloccate');
    // Il riquadro della pagina lo spiega: niente messaggio doppio.
    expect(notifiche.errore()).toBeNull();
  });

  it('se la richiesta viene chiusa senza scegliere lo dice, senza parlare di blocco', async () => {
    ambiente.risposta = 'default';
    const notifiche = servizio();

    expect(await notifiche.impostaAvvisoSquadre(true)).toBe('permesso-negato');
    expect(notifiche.errore()).toContain('chiuso la richiesta');
    expect(diagnosi().notifiche()).toBe('da-consentire');
  });

  describe('ordine delle richieste accendendo le vicine', () => {
    it('chiede le notifiche subito, dentro il tocco, e la posizione dopo', async () => {
      const ordine: string[] = [];
      ambiente.chiediPermesso.and.callFake(async () => {
        ordine.push('notifiche');
        ambiente.permessoAttuale = 'granted';
        return 'granted' as NotificationPermission;
      });
      gps.attuale.and.callFake(async () => {
        ordine.push('posizione');
        return { lat: 41.9, lng: 12.5 };
      });

      const esito = servizio().impostaAvvisoVicino(true);
      // Nessun await prima di requestPermission: è già partita, in modo sincrono.
      expect(ambiente.chiediPermesso).toHaveBeenCalled();
      expect(gps.attuale).not.toHaveBeenCalled();

      const put = await iscrizioneMandata();
      put.flush(null);
      expect(await esito).toBe('fatto');
      expect(ordine).toEqual(['notifiche', 'posizione']);
    });

    it('con le notifiche già bloccate chiede comunque la posizione e la salva, a interruttore spento', async () => {
      ambiente.permessoAttuale = 'denied';
      const notifiche = servizio();

      expect(await notifiche.impostaAvvisoVicino(true)).toBe('permesso-negato');
      expect(ambiente.chiediPermesso).not.toHaveBeenCalled();
      expect(gps.attuale).toHaveBeenCalled();
      expect(notifiche.preferenze().avvisoVicino).toBeFalse();
      expect(notifiche.preferenze().posizione?.lat).toBe(41.9);
      expect(diagnosi().notifiche()).toBe('bloccate');
      expect(diagnosi().posizione()).toBe('consentita');
      // Niente al server: lo controlla http.verify().
    });

    it('su iPhone fuori dalla Home non chiede le notifiche ma la posizione sì', async () => {
      ambiente.supportoAttuale = 'iphone-da-installare';
      const notifiche = servizio();

      expect(await notifiche.impostaAvvisoVicino(true)).toBe('permesso-negato');
      expect(ambiente.chiediPermesso).not.toHaveBeenCalled();
      expect(gps.attuale).toHaveBeenCalled();
      expect(notifiche.preferenze().posizione).not.toBeNull();
      expect(notifiche.preferenze().avvisoVicino).toBeFalse();
    });

    it('negate le notifiche alla richiesta, chiede lo stesso la posizione', async () => {
      ambiente.risposta = 'denied';
      gps.attuale.and.rejectWith(errorePerCodice(1));
      const notifiche = servizio();

      expect(await notifiche.impostaAvvisoVicino(true)).toBe('permesso-negato');
      expect(gps.attuale).toHaveBeenCalled();
      expect(diagnosi().notifiche()).toBe('bloccate');
      expect(diagnosi().posizione()).toBe('rifiutata');
    });
  });

  it('accendendo le vicine legge la posizione e la manda con il raggio', async () => {
    const notifiche = servizio();

    const esito = notifiche.impostaAvvisoVicino(true);
    const put = await iscrizioneMandata();
    expect(put.request.body.avvisoVicino).toBeTrue();
    expect(put.request.body.posizione).toEqual({ lat: 41.9, lng: 12.5 });
    expect(put.request.body.raggioKm).toBe(10);
    put.flush(null);

    expect(await esito).toBe('fatto');
    expect(notifiche.preferenze().posizione?.lat).toBe(41.9);
    expect(notifiche.preferenze().posizione?.il).toBeTruthy();
  });

  it('con la posizione negata l avviso delle vicine resta spento, e la diagnosi sa perché', async () => {
    ambiente.permessoAttuale = 'granted';
    gps.attuale.and.rejectWith(new ErrorePosizione('negata', 'Non hai dato il permesso.'));
    const notifiche = servizio();

    expect(await notifiche.impostaAvvisoVicino(true)).toBe('posizione-negata');
    expect(notifiche.preferenze().avvisoVicino).toBeFalse();
    expect(diagnosi().posizione()).toBe('rifiutata');
    expect(notifiche.errore()).toBeNull();
  });

  it('con la localizzazione spenta la diagnosi dice posizione non disponibile', async () => {
    ambiente.permessoAttuale = 'granted';
    gps.attuale.and.rejectWith(errorePerCodice(2));

    expect(await servizio().impostaAvvisoVicino(true)).toBe('posizione-negata');
    expect(diagnosi().posizione()).toBe('non-disponibile');
  });

  it('aggiornando la posizione con l avviso spento la tiene solo sul telefono', async () => {
    const notifiche = servizio();

    expect(await notifiche.aggiornaPosizione()).toBe('fatto');
    expect(notifiche.preferenze().posizione?.lat).toBe(41.9);
    // Nessuna chiamata: lo controlla http.verify().
  });

  it('riprovaPermesso chiede il permesso se il browser può ancora chiederlo', async () => {
    const notifiche = servizio();

    expect(await notifiche.riprovaPermesso()).toBe('fatto');
    expect(ambiente.chiediPermesso).toHaveBeenCalled();
    expect(diagnosi().notifiche()).toBe('consentite');
  });

  it('riprovaPermesso con le notifiche bloccate rilegge soltanto', async () => {
    ambiente.permessoAttuale = 'denied';
    const notifiche = servizio();
    expect(await notifiche.riprovaPermesso()).toBe('permesso-negato');

    ambiente.permessoAttuale = 'granted';
    expect(await notifiche.riprovaPermesso()).toBe('fatto');
    expect(ambiente.chiediPermesso).not.toHaveBeenCalled();
    expect(diagnosi().notifiche()).toBe('consentite');
  });

  it('se il server non risponde l interruttore torna spento', async () => {
    const notifiche = servizio();

    const esito = notifiche.impostaAvvisoSquadre(true);
    const put = await iscrizioneMandata();
    put.flush({ errore: 'giù' }, { status: 502, statusText: 'Bad Gateway' });

    expect(await esito).toBe('errore');
    expect(notifiche.preferenze().avvisoSquadre).toBeFalse();
    expect(notifiche.errore()).toContain('non risponde');
  });

  it('se il browser non riesce a iscriversi lo dice, senza dare la colpa al server', async () => {
    spyOn(ambiente, 'iscrivi').and.rejectWith(new DOMException('push service error', 'AbortError'));
    const notifiche = servizio();

    const esito = notifiche.impostaAvvisoSquadre(true);
    await attendi();
    http.expectOne(`${API}/chiave`).flush({ chiave: CHIAVE_VAPID });

    expect(await esito).toBe('errore');
    expect(notifiche.preferenze().avvisoSquadre).toBeFalse();
    expect(notifiche.errore()).toBeNull();
    expect(diagnosi().notifiche()).toBe('non-supportate');
  });

  it('cambiando il raggio aggiorna l iscrizione', async () => {
    localStorage.setItem(
      'trovacampo.notifiche',
      JSON.stringify({ avvisoVicino: true, raggioKm: 10, posizione: { lat: 41.9, lng: 12.5, il: '' } }),
    );
    ambiente.permessoAttuale = 'granted';

    const esito = servizio().impostaRaggio(20);
    const put = await iscrizioneMandata();
    expect(put.request.body.raggioKm).toBe(20);
    put.flush(null);
    expect(await esito).toBe('fatto');
  });

  it('spegnendo tutto cancella l iscrizione sul server e nel browser', async () => {
    localStorage.setItem('trovacampo.notifiche', JSON.stringify({ avvisoSquadre: true }));
    ambiente.permessoAttuale = 'granted';
    ambiente.iscrizione = ISCRIZIONE_FINTA;

    const esito = servizio().impostaAvvisoSquadre(false);
    await attendi();
    const cancellazione = http.expectOne((r) => r.method === 'DELETE');
    expect(cancellazione.request.body).toEqual({ endpoint: ISCRIZIONE_FINTA.endpoint });
    cancellazione.flush(null);

    expect(await esito).toBe('fatto');
    expect(ambiente.iscrizione).toBeNull();
  });

  it('seguendo una squadra con l avviso acceso aggiorna subito il server', async () => {
    localStorage.setItem('trovacampo.notifiche', JSON.stringify({ avvisoSquadre: true }));
    ambiente.permessoAttuale = 'granted';
    const notifiche = servizio();

    const esito = notifiche.segui(LODIGIANI);
    const put = await iscrizioneMandata();
    expect(put.request.body.squadre).toEqual([LODIGIANI.chiave]);
    put.flush(null);
    expect(await esito).toBe('fatto');

    const smetti = notifiche.smettiDiSeguire(LODIGIANI.chiave);
    const seconda = await iscrizioneMandata();
    expect(seconda.request.body.squadre).toEqual([]);
    seconda.flush(null);
    await smetti;
  });

  it('seguendo una squadra con l avviso spento la tiene solo sul telefono', async () => {
    const notifiche = servizio();

    expect(await notifiche.segui(LODIGIANI)).toBe('fatto');
    expect(notifiche.segue(LODIGIANI.chiave)).toBeTrue();
    // Nessuna chiamata: lo controlla http.verify().
  });

  describe('all apertura dell app', () => {
    it('rinfresca l iscrizione con la posizione nuova, se il permesso c è già', async () => {
      localStorage.setItem(
        'trovacampo.notifiche',
        JSON.stringify({ avvisoVicino: true, raggioKm: 5, posizione: { lat: 45, lng: 9, il: '' } }),
      );
      ambiente.permessoAttuale = 'granted';
      ambiente.permessoGps = 'granted';
      const notifiche = servizio();

      const avvio = notifiche.avvio();
      const put = await iscrizioneMandata();
      expect(put.request.body.posizione).toEqual({ lat: 41.9, lng: 12.5 });
      expect(put.request.body.raggioKm).toBe(5);
      put.flush(null);
      await avvio;

      expect(ambiente.registra).toHaveBeenCalled();
      expect(ambiente.chiediPermesso).not.toHaveBeenCalled();
    });

    it('senza il permesso della posizione tiene quella salvata, senza chiederla', async () => {
      localStorage.setItem(
        'trovacampo.notifiche',
        JSON.stringify({ avvisoVicino: true, posizione: { lat: 45, lng: 9, il: '' } }),
      );
      ambiente.permessoAttuale = 'granted';
      ambiente.permessoGps = 'prompt';

      const avvio = servizio().avvio();
      const put = await iscrizioneMandata();
      expect(put.request.body.posizione).toEqual({ lat: 45, lng: 9 });
      put.flush(null);
      await avvio;
      expect(gps.attuale).not.toHaveBeenCalled();
    });

    it('con gli avvisi spenti cancella un iscrizione rimasta sul server', async () => {
      ambiente.iscrizione = ISCRIZIONE_FINTA;

      const avvio = servizio().avvio();
      await attendi();
      http.expectOne((r) => r.method === 'DELETE').flush(null);
      await avvio;
      expect(ambiente.iscrizione).toBeNull();
    });

    it('con le notifiche bloccate dalle impostazioni non manda niente', async () => {
      localStorage.setItem('trovacampo.notifiche', JSON.stringify({ avvisoSquadre: true }));
      ambiente.permessoAttuale = 'denied';

      await servizio().avvio();
      expect(ambiente.registra).toHaveBeenCalled();
    });

    it('su un browser senza push non registra nemmeno il service worker', async () => {
      ambiente.supportoAttuale = 'iphone-da-installare';

      await servizio().avvio();
      expect(ambiente.registra).not.toHaveBeenCalled();
    });
  });

  it('ignora preferenze salvate non valide', () => {
    localStorage.setItem('trovacampo.notifiche', JSON.stringify({ raggioKm: 999, posizione: 'qui' }));

    expect(servizio().preferenze().raggioKm).toBe(10);
    expect(servizio().preferenze().posizione).toBeNull();
  });
});
