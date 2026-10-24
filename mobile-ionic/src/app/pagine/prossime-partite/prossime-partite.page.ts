import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonRefresher,
  IonRefresherContent,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  calendarOutline,
  checkmarkCircle,
  informationCircleOutline,
  locationOutline,
  notificationsOffOutline,
  notificationsOutline,
  peopleOutline,
  timeOutline,
} from 'ionicons/icons';
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';
import {
  AvvisoPrevisto,
  ElencoProssime,
  ProssimaPartita,
  doveSiGioca,
  motivo,
  oraDi,
  perGiorno,
  statoAvviso,
} from '../../modelli/prossime';
import { NotificheService } from '../../servizi/notifiche.service';

type Stato = 'caricamento' | 'completata' | 'errore';

/**
 * Le prossime partite per cui arriva (o è arrivato) un avviso: quelle delle
 * squadre seguite e quelle vicine, per giorno e orario, con l'ora
 * dell'avviso. Le sceglie il backend con la stessa regola delle notifiche
 * (ProssimePartite), a partire dalle scelte salvate su questo telefono.
 */
@Component({
  selector: 'pagina-prossime-partite',
  imports: [
    RouterLink,
    MenuUtenteComponent,
    IonBackButton,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
    IonRefresher,
    IonRefresherContent,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './prossime-partite.page.html',
  styleUrl: './prossime-partite.page.scss',
})
export class ProssimePartitePage {
  private readonly notifiche = inject(NotificheService);

  readonly stato = signal<Stato>('caricamento');
  readonly elenco = signal<ElencoProssime | null>(null);

  readonly preferenze = this.notifiche.preferenze;
  readonly seguite = this.notifiche.seguite;

  /** Le partite vicine contano solo con l'avviso acceso e una posizione da cui misurare. */
  readonly vicineAccese = computed(
    () => this.preferenze().avvisoVicino && this.preferenze().posizione !== null,
  );
  /** Niente da cercare: nessuna squadra seguita e partite vicine spente. */
  readonly senzaScelte = computed(() => this.seguite().length === 0 && !this.vicineAccese());

  /**
   * Le scelte ci sono ma su questo browser non arriva nessun avviso
   * (spenti, permesso negato, browser senza push): l'elenco si mostra lo
   * stesso, con un avviso in cima.
   */
  readonly senzaNotifiche = computed(
    () => !this.notifiche.avvisoAcceso('squadra') && !this.notifiche.avvisoAcceso('vicino'),
  );

  readonly giorni = computed(() => perGiorno(this.elenco()?.partite ?? []));

  /** "fino a sabato 31 ottobre" */
  readonly fino = computed(() => {
    const fino = this.elenco()?.fino;
    if (!fino) {
      return '';
    }
    return new Date(`${fino}T12:00:00Z`).toLocaleDateString('it-IT', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
      timeZone: 'Europe/Rome',
    });
  });

  /** Cosa c'è nell'elenco, detto in una frase. */
  readonly introduzione = computed(() => {
    const perche: string[] = [];
    if (this.seguite().length > 0) {
      perche.push('un’ora prima per le squadre che segui');
    }
    if (this.vicineAccese()) {
      perche.push(`mezz’ora prima per quelle entro ${this.preferenze().raggioKm} km da te`);
    }
    const fino = this.fino() ? `, fino a ${this.fino()}` : '';
    return `Le partite per cui ti arriva un avviso${fino}: ${perche.join(', ')}.`;
  });

  readonly nessunaPartita = computed(() => {
    const dove: string[] = [];
    if (this.seguite().length > 0) {
      dove.push('delle squadre che segui');
    }
    if (this.vicineAccese()) {
      dove.push(`sui campi entro ${this.preferenze().raggioKm} km`);
    }
    return (
      `Fino a ${this.fino()} non ci sono partite ${dove.join(' né ')} ` +
      'nei programmi gare pubblicati finora.'
    );
  });

  readonly motivo = motivo;
  readonly oraDi = oraDi;
  readonly doveSiGioca = doveSiGioca;

  constructor() {
    addIcons({
      calendarOutline,
      checkmarkCircle,
      informationCircleOutline,
      locationOutline,
      notificationsOffOutline,
      notificationsOutline,
      peopleOutline,
      timeOutline,
    });
  }

  /** A ogni ingresso: squadre e preferenze si cambiano dalle altre pagine. */
  ionViewWillEnter(): void {
    this.notifiche.rileggiPermesso();
    void this.carica();
  }

  async carica(): Promise<void> {
    if (this.senzaScelte()) {
      this.elenco.set(null);
      this.stato.set('completata');
      return;
    }
    // Al primo caricamento la rotella; ricaricando resta l'elenco di prima.
    if (!this.elenco()) {
      this.stato.set('caricamento');
    }
    try {
      this.elenco.set(await this.notifiche.prossime());
      this.stato.set('completata');
    } catch {
      this.stato.set('errore');
    }
  }

  async ricarica(evento: CustomEvent): Promise<void> {
    await this.carica();
    await (evento.target as HTMLIonRefresherElement | null)?.complete();
  }

  statoAvviso(avviso: AvvisoPrevisto): string {
    return statoAvviso(avviso, this.notifiche.avvisoAcceso(avviso.tipo));
  }

  /** Per il colore della riga dell'avviso. */
  tonoAvviso(avviso: AvvisoPrevisto): 'inviato' | 'previsto' | 'spento' {
    if (avviso.stato === 'inviato') {
      return 'inviato';
    }
    return this.notifiche.avvisoAcceso(avviso.tipo) && avviso.stato === 'previsto'
      ? 'previsto'
      : 'spento';
  }

  traccia(partita: ProssimaPartita): string {
    return `${partita.dataOra}|${partita.casa}|${partita.ospite}`;
  }
}
