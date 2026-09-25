import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonSpinner,
  IonTitle,
  IonToggle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  checkmarkCircle,
  helpCircleOutline,
  locateOutline,
  locationOutline,
  notificationsOffOutline,
  notificationsOutline,
  peopleOutline,
} from 'ionicons/icons';
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';
import { RiquadroDiagnosiComponent } from '../../componenti/riquadro-diagnosi/riquadro-diagnosi.component';
import { DiagnosiService } from '../../servizi/diagnosi.service';
import {
  istruzioniNotifiche,
  istruzioniPosizione,
  statoNotifiche,
  statoPosizione,
} from '../../servizi/istruzioni';
import { NotificheService, RAGGI_KM } from '../../servizi/notifiche.service';

/**
 * "oggi alle 14:32", "ieri alle 9:05", "il 21 settembre alle 18:00": quando
 * è stata salvata la posizione delle partite vicine.
 */
export function quandoSalvata(iso: string, adesso: Date = new Date()): string {
  const data = new Date(iso);
  if (Number.isNaN(data.getTime())) {
    return '';
  }
  const ora = data.toLocaleTimeString('it-IT', { hour: 'numeric', minute: '2-digit' });
  const giorno = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const giorni = Math.round((giorno(adesso) - giorno(data)) / 86_400_000);
  if (giorni === 0) {
    return `oggi alle ${ora}`;
  }
  if (giorni === 1) {
    return `ieri alle ${ora}`;
  }
  const quando = data.toLocaleDateString('it-IT', { day: 'numeric', month: 'long' });
  return `il ${quando} alle ${ora}`;
}

/**
 * Le notifiche push: un'ora prima delle partite delle squadre seguite,
 * mezz'ora prima di quelle vicine. Senza login, legate a questo browser;
 * tutte e due spente finché non le si accende da qui.
 */
@Component({
  selector: 'pagina-notifiche',
  imports: [
    RouterLink,
    MenuUtenteComponent,
    RiquadroDiagnosiComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
    IonSpinner,
    IonTitle,
    IonToggle,
    IonToolbar,
  ],
  templateUrl: './notifiche.page.html',
  styleUrl: './notifiche.page.scss',
})
export class NotifichePage {
  readonly notifiche = inject(NotificheService);
  private readonly diagnosi = inject(DiagnosiService);

  readonly raggi = RAGGI_KM;
  readonly preferenze = this.notifiche.preferenze;
  readonly seguite = this.notifiche.seguite;
  readonly supporto = this.notifiche.supporto;
  readonly errore = this.notifiche.errore;
  readonly occupato = this.notifiche.occupato;

  /**
   * Gli interruttori si toccano solo dove le notifiche possono arrivare, o
   * almeno essere chieste: con le notifiche bloccate l'interruttore delle
   * vicine resta toccabile, perché chiede comunque la posizione e il
   * riquadro dice come sbloccare il resto.
   */
  readonly utilizzabile = computed(() => this.supporto() === 'supportato');

  /** La riga in cima: cosa c'è e cosa manca, a colpo d'occhio. */
  readonly statoNotifiche = computed(() => statoNotifiche(this.diagnosi.notifiche()));
  readonly statoPosizione = computed(() => statoPosizione(this.diagnosi.posizione()));

  readonly riquadroNotifiche = computed(() =>
    istruzioniNotifiche(this.diagnosi.notifiche(), this.diagnosi.piattaforma),
  );
  readonly riquadroPosizione = computed(() =>
    istruzioniPosizione(this.diagnosi.posizione(), this.diagnosi.piattaforma),
  );

  /**
   * L'ultimo avviso che si è provato ad accendere: il riquadro delle
   * notifiche compare lì sotto, vicino al dito, e "Riprova" rifà proprio
   * quell'accensione. Prima di ogni tentativo il riquadro sta in cima.
   */
  readonly tentativo = signal<'squadre' | 'vicino' | null>(null);
  readonly doveNotifiche = computed(() => this.tentativo() ?? 'cima');

  readonly posizioneSalvata = computed(() => {
    const posizione = this.preferenze().posizione;
    return posizione ? quandoSalvata(posizione.il) : null;
  });

  constructor() {
    addIcons({
      alertCircleOutline,
      checkmarkCircle,
      helpCircleOutline,
      locateOutline,
      locationOutline,
      notificationsOffOutline,
      notificationsOutline,
      peopleOutline,
    });
  }

  /** A ogni ingresso: i permessi si possono cambiare dalle impostazioni, a pagina chiusa. */
  ionViewWillEnter(): void {
    this.notifiche.rileggiPermesso();
    void this.diagnosi.aggiornaPosizione();
  }

  async cambiaSquadre(evento: CustomEvent<{ checked: boolean }>): Promise<void> {
    // Preso prima dell'attesa: dopo, l'evento non è più quello in corso.
    const interruttore = evento.target as HTMLIonToggleElement | null;
    if (evento.detail.checked) {
      this.tentativo.set('squadre');
    }
    await this.notifiche.impostaAvvisoSquadre(evento.detail.checked);
    riallinea(interruttore, this.preferenze().avvisoSquadre);
  }

  async cambiaVicino(evento: CustomEvent<{ checked: boolean }>): Promise<void> {
    // Preso prima dell'attesa: dopo, l'evento non è più quello in corso.
    const interruttore = evento.target as HTMLIonToggleElement | null;
    if (evento.detail.checked) {
      this.tentativo.set('vicino');
    }
    await this.notifiche.impostaAvvisoVicino(evento.detail.checked);
    riallinea(interruttore, this.preferenze().avvisoVicino);
  }

  /**
   * "Riprova" sulle notifiche: rifà l'accensione che non era riuscita (la
   * richiesta parte dentro il tocco su Riprova, come vuole Safari), o, se
   * non ce n'era una, richiede il permesso dove si può e rilegge lo stato.
   */
  async riprovaNotifiche(): Promise<void> {
    const preferenze = this.preferenze();
    if (this.tentativo() === 'vicino' && !preferenze.avvisoVicino) {
      await this.notifiche.impostaAvvisoVicino(true);
    } else if (this.tentativo() === 'squadre' && !preferenze.avvisoSquadre) {
      await this.notifiche.impostaAvvisoSquadre(true);
    } else {
      await this.notifiche.riprovaPermesso();
    }
    await this.diagnosi.aggiornaPosizione();
  }

  /**
   * "Riprova" sulla posizione: se si stava accendendo l'avviso delle
   * vicine lo si riaccende (notifiche e posizione, nello stesso ordine),
   * altrimenti si rilegge solo la posizione. In tutti e due i casi il
   * browser, se può, la richiede.
   */
  async riprovaPosizione(): Promise<void> {
    if (this.tentativo() === 'vicino' && !this.preferenze().avvisoVicino && this.utilizzabile()) {
      await this.notifiche.impostaAvvisoVicino(true);
    } else {
      await this.notifiche.aggiornaPosizione();
    }
  }

  async cambiaRaggio(valore: unknown): Promise<void> {
    await this.notifiche.impostaRaggio(Number(valore));
  }

  async aggiornaPosizione(): Promise<void> {
    await this.notifiche.aggiornaPosizione();
  }

  async smetti(chiave: string): Promise<void> {
    await this.notifiche.smettiDiSeguire(chiave);
  }
}

/**
 * Se l'accensione non è riuscita (permesso negato, server giù) l'interruttore
 * torna com'era: il binding [checked] non se ne accorge da solo, perché il
 * valore nel segnale non è mai cambiato.
 */
function riallinea(interruttore: HTMLIonToggleElement | null, acceso: boolean): void {
  if (interruttore && interruttore.checked !== acceso) {
    interruttore.checked = acceso;
  }
}
