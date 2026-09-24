import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { AmbientePush, SupportoPush } from './ambiente-push';
import { DiagnosiService } from './diagnosi.service';
import { PosizioneService } from './posizione.service';
import { SquadraSeguita, SquadreSeguiteService } from './squadre-seguite.service';

const CHIAVE = 'trovacampo.notifiche';

/** I raggi fra cui scegliere per le partite vicine. */
export const RAGGI_KM = [5, 10, 20] as const;
export const RAGGIO_PREDEFINITO_KM = 10;

/** La posizione mandata al server, e quando: la pagina lo mostra. */
export interface PosizioneSalvata {
  lat: number;
  lng: number;
  /** ISO 8601. */
  il: string;
}

/** Le scelte di chi usa l'app, sul dispositivo. Entrambi gli avvisi partono spenti. */
export interface PreferenzeNotifiche {
  avvisoSquadre: boolean;
  avvisoVicino: boolean;
  raggioKm: number;
  posizione: PosizioneSalvata | null;
}

const PREDEFINITE: PreferenzeNotifiche = {
  avvisoSquadre: false,
  avvisoVicino: false,
  raggioKm: RAGGIO_PREDEFINITO_KM,
  posizione: null,
};

/**
 * Il browser non è riuscito a iscriversi al suo servizio push: navigazione
 * privata (Chrome non lo permette), servizio push irraggiungibile, o un
 * Chromium senza servizi Google. Non è colpa del server di TrovaCampo, e il
 * messaggio lo deve dire.
 */
class ErroreIscrizione extends Error {}

/** Com'è andata un'accensione, per il messaggio della pagina. */
export type Esito = 'fatto' | 'permesso-negato' | 'posizione-negata' | 'troppe' | 'errore';

/**
 * Le notifiche push, senza login: il browser si iscrive al servizio push con
 * la chiave VAPID del backend, e manda al backend l'iscrizione con le
 * preferenze. Il backend fa il resto (PianificatoreNotifiche): un'ora prima
 * delle partite delle squadre seguite, mezz'ora prima di quelle vicine.
 *
 * Le preferenze stanno sul dispositivo e il server ne ha una copia: a ogni
 * cambio e a ogni apertura dell'app gliela si rimanda intera. Così il server
 * non deve fidarsi di niente che non abbia appena ricevuto, e un'iscrizione
 * rinfrescata di recente è di un telefono che c'è ancora (quelle ferme da un
 * anno le butta).
 */
@Injectable({ providedIn: 'root' })
export class NotificheService {
  private readonly http = inject(HttpClient);
  private readonly ambiente = inject(AmbientePush);
  private readonly gps = inject(PosizioneService);
  private readonly squadre = inject(SquadreSeguiteService);
  private readonly diagnosi = inject(DiagnosiService);
  private readonly base = `${environment.apiUrl}/api/notifiche`;

  readonly supporto = signal<SupportoPush>(this.ambiente.supporto());
  readonly permesso = signal<NotificationPermission>(this.ambiente.permesso());
  readonly preferenze = signal<PreferenzeNotifiche>(ricordate());
  /** Un'operazione in corso: la pagina spegne gli interruttori nel frattempo. */
  readonly occupato = signal(false);
  /** L'ultimo problema, da mostrare; null se è andato tutto bene. */
  readonly errore = signal<string | null>(null);

  readonly seguite = this.squadre.seguite;
  readonly qualcheAvviso = computed(
    () => this.preferenze().avvisoSquadre || this.preferenze().avvisoVicino,
  );

  /** Le operazioni con il server una dopo l'altra: due cambi veloci non si sorpassano. */
  private coda: Promise<unknown> = Promise.resolve();

  /**
   * All'apertura dell'app: registra il service worker e, se c'è un avviso
   * acceso, rinfresca l'iscrizione, con la posizione nuova per le partite
   * vicine se il permesso c'è già. Non chiede mai niente: un permesso a
   * sorpresa all'apertura verrebbe negato, e a ragione.
   */
  async avvio(): Promise<void> {
    if (this.supporto() !== 'supportato') {
      return;
    }
    try {
      await this.ambiente.registra();
    } catch {
      return;
    }
    this.permesso.set(this.ambiente.permesso());
    if (!this.qualcheAvviso()) {
      // Avvisi spenti: se il server ha ancora un'iscrizione (uno spegnimento
      // finito senza rete), la si cancella adesso.
      await this.metti(() => this.cancellaSulServer()).catch(() => undefined);
      return;
    }
    if (this.permesso() !== 'granted') {
      // Permesso tolto dalle impostazioni: le notifiche non arriverebbero,
      // e la pagina Notifiche lo dice.
      return;
    }
    if (this.preferenze().avvisoVicino && (await this.ambiente.permessoPosizione()) === 'granted') {
      try {
        this.ricordaPosizione(await this.gps.attuale());
      } catch {
        // Si tiene l'ultima posizione salvata.
      }
    }
    await this.metti(() => this.mandaAlServer()).catch(() => undefined);
  }

  async impostaAvvisoSquadre(acceso: boolean): Promise<Esito> {
    return this.cambia(async () => {
      if (acceso && !(await this.assicuraPermesso())) {
        return 'permesso-negato';
      }
      return this.salvaESincronizza({ ...this.preferenze(), avvisoSquadre: acceso });
    });
  }

  /**
   * Accendendo servono due permessi, notifiche e posizione, e si chiedono
   * sempre tutti e due, in quest'ordine:
   *
   * 1. le notifiche per prime, perché Safari (e Firefox) accettano
   *    Notification.requestPermission solo dentro il tocco dell'utente, e
   *    il tocco "scade" dopo un'attesa: dopo la richiesta della posizione,
   *    che aspetta la risposta di chi usa il telefono, sarebbe persa.
   *    Fino a requestPermission qui non c'è nessun await, così la richiesta
   *    parte ancora dentro il tocco sull'interruttore;
   * 2. la posizione dopo, anche se le notifiche sono negate o qui non ci
   *    sono (iPhone fuori dalla Home, browser di WhatsApp...):
   *    getCurrentPosition non ha bisogno del tocco, quindi la richiesta
   *    compare comunque. Così chi accende vede almeno quella, la posizione
   *    resta salvata sul telefono, e il riquadro della pagina dice cosa
   *    manca ancora. L'avviso però resta spento finché le notifiche non
   *    vanno: acceso, prometterebbe avvisi che non arrivano.
   */
  async impostaAvvisoVicino(acceso: boolean): Promise<Esito> {
    return this.cambia(async () => {
      if (!acceso) {
        // Spento, la posizione non serve più: si dimentica anche qui, non solo sul server.
        return this.salvaESincronizza({ ...this.preferenze(), avvisoVicino: false, posizione: null });
      }
      const notificheOk = await this.assicuraPermesso();
      const posizione = await this.leggiPosizione();
      if (!notificheOk) {
        if (posizione) {
          this.salva({ ...this.preferenze(), posizione });
        }
        return 'permesso-negato';
      }
      if (!posizione) {
        return 'posizione-negata';
      }
      return this.salvaESincronizza({ ...this.preferenze(), avvisoVicino: true, posizione });
    });
  }

  async impostaRaggio(raggioKm: number): Promise<Esito> {
    const valido = (RAGGI_KM as readonly number[]).includes(raggioKm) ? raggioKm : RAGGIO_PREDEFINITO_KM;
    return this.cambia(() => this.salvaESincronizza({ ...this.preferenze(), raggioKm: valido }));
  }

  /**
   * Rilegge la posizione. Con l'avviso spento resta solo sul telefono: al
   * server non serve, e su un browser senza push non ci sarebbe nemmeno
   * un'iscrizione da aggiornare.
   */
  async aggiornaPosizione(): Promise<Esito> {
    return this.cambia(async () => {
      const posizione = await this.leggiPosizione();
      if (!posizione) {
        return 'posizione-negata';
      }
      if (!this.preferenze().avvisoVicino) {
        this.salva({ ...this.preferenze(), posizione });
        return 'fatto';
      }
      return this.salvaESincronizza({ ...this.preferenze(), posizione });
    });
  }

  /**
   * "Riprova" sulle notifiche, quando non c'era un avviso da accendere:
   * se il browser può ancora chiedere, chiede (dentro il tocco su Riprova),
   * altrimenti rilegge soltanto come stanno le cose.
   */
  async riprovaPermesso(): Promise<Esito> {
    return this.cambia(async () => ((await this.assicuraPermesso()) ? 'fatto' : 'permesso-negato'));
  }

  /** Il permesso si cambia anche dalle impostazioni del browser, a pagina chiusa. */
  rileggiPermesso(): void {
    this.supporto.set(this.ambiente.supporto());
    this.permesso.set(this.ambiente.permesso());
    this.diagnosi.aggiornaNotifiche();
  }

  segue(chiave: string): boolean {
    return this.squadre.segue(chiave);
  }

  /**
   * Segue una squadra. Sul dispositivo sempre; al server solo se l'avviso
   * delle squadre è acceso (altrimenti ci arriva quando lo si accende).
   */
  async segui(squadra: SquadraSeguita): Promise<Esito> {
    if (!this.squadre.aggiungi(squadra)) {
      this.errore.set('Segui già 30 squadre: smetti di seguirne una per aggiungerne altre.');
      return 'troppe';
    }
    return this.squadreCambiate();
  }

  async smettiDiSeguire(chiave: string): Promise<Esito> {
    this.squadre.togli(chiave);
    return this.squadreCambiate();
  }

  private async squadreCambiate(): Promise<Esito> {
    if (!this.preferenze().avvisoSquadre || this.permesso() !== 'granted') {
      return 'fatto';
    }
    return this.cambia(async () => {
      try {
        await this.metti(() => this.mandaAlServer());
        return 'fatto';
      } catch {
        this.errore.set(
          'Squadra salvata sul telefono, ma non sul server: riprova quando torna la rete ' +
            '(basta riaprire l’app).',
        );
        return 'errore';
      }
    });
  }

  private async cambia(operazione: () => Promise<Esito>): Promise<Esito> {
    this.occupato.set(true);
    this.errore.set(null);
    try {
      return await operazione();
    } finally {
      this.occupato.set(false);
    }
  }

  /**
   * Il permesso delle notifiche, chiesto solo dove può servire: su un
   * browser senza push (iPhone fuori dalla Home, browser di un'app) non si
   * chiede niente, e il motivo lo mostra il riquadro della pagina. Nessun
   * await prima di requestPermission: deve partire dentro il tocco.
   */
  private async assicuraPermesso(): Promise<boolean> {
    this.supporto.set(this.ambiente.supporto());
    let permesso = this.ambiente.permesso();
    if (this.supporto() === 'supportato' && permesso === 'default') {
      permesso = await this.ambiente.chiediPermesso();
      if (permesso === 'default') {
        // Chrome chiude la richiesta senza risposta se la si ignora o la si
        // chiude con la X: non è un blocco, basta riprovare.
        this.errore.set(
          'Hai chiuso la richiesta senza scegliere: riprova e tocca «Consenti».',
        );
      }
    }
    this.permesso.set(permesso);
    this.diagnosi.aggiornaNotifiche();
    return this.supporto() === 'supportato' && permesso === 'granted';
  }

  /** L'esito va anche alla diagnosi, che mostra cosa blocca e come sbloccarlo. */
  private async leggiPosizione(): Promise<PosizioneSalvata | null> {
    try {
      const { lat, lng } = await this.gps.attuale();
      await this.diagnosi.esitoPosizione(null);
      return { lat, lng, il: new Date().toISOString() };
    } catch (errore) {
      await this.diagnosi.esitoPosizione(errore);
      return null;
    }
  }

  private ricordaPosizione(posizione: { lat: number; lng: number }): void {
    this.salva({ ...this.preferenze(), posizione: { ...posizione, il: new Date().toISOString() } });
  }

  /**
   * Salva le preferenze nuove se il server le accetta; se non risponde,
   * restano quelle di prima, così l'interruttore non dice acceso mentre il
   * server non lo sa.
   */
  private async salvaESincronizza(nuove: PreferenzeNotifiche): Promise<Esito> {
    const prima = this.preferenze();
    this.salva(nuove);
    if ((nuove.avvisoSquadre || nuove.avvisoVicino) && this.ambiente.permesso() !== 'granted') {
      // Permesso tolto dalle impostazioni: la scelta resta sul telefono, e
      // al server arriva quando il permesso torna (la pagina lo dice).
      return 'fatto';
    }
    try {
      await this.metti(() =>
        nuove.avvisoSquadre || nuove.avvisoVicino ? this.mandaAlServer() : this.cancellaSulServer(),
      );
      return 'fatto';
    } catch (errore) {
      this.salva(prima);
      if (errore instanceof ErroreIscrizione) {
        // Non è colpa del server: il riquadro "qui non funzionano" spiega
        // navigazione privata e dintorni.
        this.diagnosi.segnalaIscrizioneFallita();
      } else {
        this.errore.set('Il server delle notifiche non risponde: riprova tra poco.');
      }
      return 'errore';
    }
  }

  private metti<T>(operazione: () => Promise<T>): Promise<T> {
    const risultato = this.coda.then(operazione, operazione);
    this.coda = risultato.catch(() => undefined);
    return risultato;
  }

  private async mandaAlServer(): Promise<void> {
    const { chiave } = await firstValueFrom(this.http.get<{ chiave: string }>(`${this.base}/chiave`));
    const iscrizione = await this.ambiente.iscrivi(chiave).catch((errore: unknown) => {
      throw new ErroreIscrizione(String(errore));
    });
    this.diagnosi.segnalaIscrizioneRiuscita();
    const preferenze = this.preferenze();
    await firstValueFrom(
      this.http.put(`${this.base}/iscrizione`, {
        ...iscrizione,
        squadre: this.squadre.chiavi(),
        avvisoSquadre: preferenze.avvisoSquadre,
        avvisoVicino: preferenze.avvisoVicino,
        posizione:
          preferenze.avvisoVicino && preferenze.posizione
            ? { lat: preferenze.posizione.lat, lng: preferenze.posizione.lng }
            : null,
        raggioKm: preferenze.raggioKm,
      }),
    );
  }

  /**
   * Spento tutto: il server dimentica l'iscrizione (e la posizione), e il
   * browser la chiude. Prima il server: se non risponde, l'iscrizione resta
   * e al prossimo avvio si riprova.
   */
  private async cancellaSulServer(): Promise<void> {
    const iscrizione = await this.ambiente.iscrizioneAttuale();
    if (!iscrizione) {
      return;
    }
    await firstValueFrom(
      this.http.delete(`${this.base}/iscrizione`, { body: { endpoint: iscrizione.endpoint } }),
    );
    await this.ambiente.disiscrivi();
  }

  private salva(preferenze: PreferenzeNotifiche): void {
    this.preferenze.set(preferenze);
    try {
      localStorage.setItem(CHIAVE, JSON.stringify(preferenze));
    } catch {
      // Archivio bloccato: le scelte valgono finché la pagina resta aperta.
    }
  }
}

function ricordate(): PreferenzeNotifiche {
  try {
    const lette = JSON.parse(localStorage.getItem(CHIAVE) ?? 'null') as Partial<PreferenzeNotifiche> | null;
    if (!lette || typeof lette !== 'object') {
      return PREDEFINITE;
    }
    const posizione = lette.posizione;
    return {
      avvisoSquadre: lette.avvisoSquadre === true,
      avvisoVicino: lette.avvisoVicino === true,
      raggioKm: (RAGGI_KM as readonly number[]).includes(lette.raggioKm ?? -1)
        ? lette.raggioKm!
        : RAGGIO_PREDEFINITO_KM,
      posizione:
        posizione && typeof posizione.lat === 'number' && typeof posizione.lng === 'number'
          ? posizione
          : null,
    };
  } catch {
    return PREDEFINITE;
  }
}
