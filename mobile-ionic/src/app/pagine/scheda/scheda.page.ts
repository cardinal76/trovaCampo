import {
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  IonAccordion,
  IonAccordionGroup,
  IonBackButton,
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonNote,
  IonSpinner,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  calendarOutline,
  create,
  notifications,
  notificationsOutline,
  peopleOutline,
  timeOutline,
  trophyOutline,
} from 'ionicons/icons';
import * as L from 'leaflet';
import {
  Societa,
  SocietaGeolocalizzata,
  haCoordinate,
  indirizzoCompleto,
  nomeCompleto,
} from '../../modelli/societa';
import {
  GIORNI_PARTITE_SCHEDA,
  Partita,
  campionatoPartita,
  dataPartita,
  quandoPartita,
  squadrePartita,
} from '../../modelli/partita';
import { Squadra, dettaglioSquadra } from '../../modelli/squadra';
import { iconaCampo } from '../../mappa/icona-campo';
import { amministratoreRicordato } from '../../servizi/amministratore-ricordato';
import { NotificheService } from '../../servizi/notifiche.service';
import { SocietaService } from '../../servizi/societa.service';

type Stato = 'caricamento' | 'completata' | 'errore';

/**
 * Zoom della mappa della scheda: abbastanza vicino da riconoscere le vie
 * intorno al campo, senza perdere il quartiere.
 *
 * Il nome accanto all'icona resta spento, perché la mappa non riceve mai la
 * classe mostra-nomi-campi (non chiama aggiornaNomiCampi): qui ripeterebbe il
 * titolo della scheda, poche righe più su.
 */
const ZOOM_SCHEDA = 16;

/**
 * Funzioni 2 e 3 riunite in un'unica schermata, come nel mockup
 * grafica/anagraficaSocietà.jpg: anagrafica della società e campionati a cui
 * partecipa, distinti fra agonistica e scuola calcio.
 */
@Component({
  selector: 'pagina-scheda',
  imports: [
    RouterLink,
    IonAccordion,
    IonAccordionGroup,
    IonBackButton,
    IonBadge,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
    IonItem,
    IonLabel,
    IonList,
    IonListHeader,
    IonNote,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './scheda.page.html',
  styleUrl: './scheda.page.scss',
})
export class SchedaPage implements OnDestroy {
  private readonly rotta = inject(ActivatedRoute);
  private readonly service = inject(SocietaService);
  private readonly zona = inject(NgZone);
  private readonly notifiche = inject(NotificheService);
  private readonly avvisi = inject(ToastController);
  private readonly router = inject(Router);

  private readonly contenitoreMappa = viewChild<ElementRef<HTMLDivElement>>('contenitoreMappa');
  private mappa: L.Map | null = null;
  private segnaposto: L.Marker | null = null;

  readonly id = this.rotta.snapshot.paramMap.get('id') ?? '';
  readonly societa = signal<Societa | null>(null);
  readonly stato = signal<Stato>('caricamento');
  /** Le squadre dall'anagrafica di presenze: si caricano a parte, e possono mancare. */
  readonly squadre = signal<Squadra[]>([]);
  readonly statoSquadre = signal<Stato>('caricamento');
  readonly dettaglioSquadra = dettaglioSquadra;
  /**
   * Le prossime partite su questo campo, dal calendario di presenze: anche
   * loro a parte, e solo per un campo che viene da lì.
   */
  readonly partite = signal<Partita[]>([]);
  readonly statoPartite = signal<Stato>('caricamento');
  readonly giorniPartite = GIORNI_PARTITE_SCHEDA;
  readonly quandoPartita = quandoPartita;
  readonly squadrePartita = squadrePartita;
  readonly campionatoPartita = campionatoPartita;
  readonly dataPartita = (partita: Partita) => dataPartita(partita);
  /** Il pulsante "Modifica": solo se su questo browser è entrato un amministratore. */
  readonly amministratore = signal(amministratoreRicordato());

  readonly indirizzoCampo = computed(() => {
    const societa = this.societa();
    return societa ? indirizzoCompleto(societa) : '';
  });

  readonly titolo = computed(() => {
    const societa = this.societa();
    return societa ? nomeCompleto(societa) : 'Scheda società';
  });

  /**
   * La società da mostrare sulla mappa, se se ne conosce la posizione. Per un
   * campo segnalato a mano e non geocodificato è null: al posto della mappa
   * la scheda lo dice, invece di mostrarne una vuota in mezzo all'oceano.
   */
  readonly campoGeolocalizzato = computed<SocietaGeolocalizzata | null>(() => {
    const societa = this.societa();
    return societa && haCoordinate(societa) ? societa : null;
  });

  /** Un campo inserito a mano con la sola Funzione 1 non ha anagrafica. */
  /**
   * Presidente, sede e contatti non li scrivono i comunicati: mancano anche
   * alle società che vengono dall'anagrafica di presenze, che a mano non sono
   * state inserite. Il motivo cambia, e la frase con lui.
   */
  readonly testoSenzaAnagrafica = computed(() =>
    this.societa()?.anagraficaSocietaId || this.squadre().length > 0
      ? 'Presidente, sede e contatti non sono ancora stati inseriti.'
      : 'Anagrafica non ancora disponibile per questo campo (inserito manualmente).',
  );

  readonly haAnagrafica = computed(() => {
    const societa = this.societa();
    return Boolean(
      societa &&
        (societa.presidente ||
          societa.indirizzoSede ||
          societa.telefono ||
          societa.email ||
          societa.sitoWeb ||
          societa.matricola),
    );
  });

  readonly campionati = computed(() => this.societa()?.campionati ?? []);
  readonly agonistica = computed(() => this.campionati().filter((c) => c.tipo === 'Agonistica'));
  readonly scuolaCalcio = computed(() =>
    this.campionati().filter((c) => c.tipo === 'ScuolaCalcio'),
  );

  /** Il numero sull'intestazione piegata dei campionati: si legge senza aprirla. */
  readonly numeroCampionati = computed(
    () => this.squadre().length + this.agonistica().length + this.scuolaCalcio().length,
  );

  /**
   * Le sezioni aperte all'arrivo: le partite, se il campo ne può avere,
   * perché è quello che cerca chi sta andando al campo; altrimenti i
   * campionati. L'anagrafica resta chiusa: serve di rado, e i contatti sono
   * spesso vuoti.
   */
  readonly sezioniAperte = computed(() =>
    this.societa()?.anagraficaImpiantoId ? ['partite'] : ['campionati'],
  );

  readonly prezziScuolaCalcio = computed(() => {
    const societa = this.societa();

    if (!societa || societa.scuolaCalcio === undefined) {
      return null;
    }

    if (!societa.scuolaCalcio) {
      return 'No';
    }

    return societa.prezziScuolaCalcio ? `Sì — ${societa.prezziScuolaCalcio}` : 'Sì';
  });

  constructor() {
    addIcons({
      calendarOutline,
      create,
      notifications,
      notificationsOutline,
      peopleOutline,
      timeOutline,
      trophyOutline,
    });

    // La mappa nasce quando il suo contenitore compare (cioè a dati caricati e
    // solo se il campo ha una posizione) e si riallinea se i dati cambiano,
    // per esempio tornando qui dopo aver corretto l'indirizzo.
    effect(() => {
      const contenitore = this.contenitoreMappa();
      const campo = this.campoGeolocalizzato();

      if (contenitore && campo) {
        this.disegnaMappa(contenitore.nativeElement, campo);
      } else {
        // Il contenitore è sparito (il campo ha perso la posizione): la mappa
        // legata a lui va buttata, e se torna se ne crea una nuova.
        this.rimuoviMappa();
      }
    });
  }

  /** Le squadre seguite, lette dal segnale: la campanella cambia appena le si tocca. */
  readonly chiaviSeguite = computed(() => new Set(this.notifiche.seguite().map((s) => s.chiave)));

  seguita(squadra: Squadra): boolean {
    return !!squadra.chiave && this.chiaviSeguite().has(squadra.chiave);
  }

  /**
   * La campanella accanto a una squadra: la segue o smette di seguirla. Chi
   * la segue per la prima volta con l'avviso spento lo scopre subito, con un
   * rimando alla pagina Notifiche.
   */
  async seguiOSmetti(squadra: Squadra): Promise<void> {
    const societa = this.societa();
    if (!squadra.chiave || !societa) {
      return;
    }
    if (this.seguita(squadra)) {
      await this.notifiche.smettiDiSeguire(squadra.chiave);
      await this.avvisa(`Non segui più ${squadra.campionato}.`);
      return;
    }
    const esito = await this.notifiche.segui({
      chiave: squadra.chiave,
      societa: nomeCompleto(societa),
      societaId: societa.id,
      campionato: squadra.campionato,
      dettaglio: dettaglioSquadra(squadra),
    });
    if (esito === 'troppe') {
      await this.avvisa('Segui già 30 squadre: smetti di seguirne una dalla pagina Notifiche.', true);
    } else if (this.notifiche.preferenze().avvisoSquadre) {
      await this.avvisa(`Segui ${squadra.campionato}: avviso un’ora prima di ogni partita.`);
    } else {
      await this.avvisa(`Segui ${squadra.campionato}. Per gli avvisi accendi le notifiche.`, true);
    }
  }

  private async avvisa(messaggio: string, conRimando = false): Promise<void> {
    const avviso = await this.avvisi.create({
      message: messaggio,
      duration: conRimando ? 5000 : 2500,
      position: 'bottom',
      buttons: conRimando
        ? [{ text: 'Notifiche', handler: () => void this.router.navigateByUrl('/notifiche') }]
        : [],
    });
    await avviso.present();
  }

  ngOnDestroy(): void {
    this.rimuoviMappa();
  }

  private rimuoviMappa(): void {
    this.mappa?.remove();
    this.mappa = null;
    this.segnaposto = null;
  }

  /** Rientrando nella pagina il contenitore è stato ridimensionato dalla transizione. */
  ionViewDidEnter(): void {
    this.mappa?.invalidateSize();
  }

  /**
   * A ogni ingresso e non solo alla creazione: tornando dalla modifica la
   * scheda deve mostrare i dati appena salvati.
   */
  ionViewWillEnter(): void {
    this.amministratore.set(amministratoreRicordato());
    this.service.perId(this.id).subscribe({
      next: (dati) => {
        this.societa.set(dati);
        this.stato.set('completata');
        this.caricaSquadre(dati);
        this.caricaPartite(dati);
      },
      error: () => this.stato.set('errore'),
    });
  }

  /**
   * Mappa con il solo campo di questa società, segnato con l'icona del campo
   * da calcio usata dalle altre mappe. Nessun altro campo: qui interessa
   * dove si gioca, non cosa c'è intorno.
   */
  private disegnaMappa(contenitore: HTMLElement, campo: SocietaGeolocalizzata): void {
    // Leaflet lavora sul DOM: fuori dalla zona Angular, così pan e zoom non
    // fanno girare il rilevamento delle modifiche a ogni evento.
    this.zona.runOutsideAngular(() => {
      // Un contenitore nuovo (ricreato dal template) vuole una mappa nuova.
      if (this.mappa && this.mappa.getContainer() !== contenitore) {
        this.rimuoviMappa();
      }
      if (!this.mappa) {
        this.mappa = L.map(contenitore, {
          attributionControl: true,
          // La scheda è una pagina che scorre: con la rotella attiva il
          // puntatore sopra la mappa zoomerebbe invece di scorrere la pagina.
          scrollWheelZoom: false,
        });

        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution: '© OpenStreetMap',
        }).addTo(this.mappa);
      }

      const posizione = L.latLng(campo.lat, campo.lng);
      this.mappa.setView(posizione, ZOOM_SCHEDA);

      this.segnaposto?.remove();
      this.segnaposto = L.marker(posizione, {
        icon: iconaCampo(campo),
        interactive: false,
        keyboard: false,
      }).addTo(this.mappa);

      // Le dimensioni definitive arrivano solo a transizione di pagina finita.
      setTimeout(() => this.mappa?.invalidateSize(), 200);
    });
  }

  /**
   * Per ogni società, anche quelle non ancora legate all'anagrafica di
   * presenze: il backend le cerca per nome. Se presenze non risponde la
   * scheda resta com'è, con un avviso al posto dei campionati.
   */
  private caricaSquadre(societa: Societa): void {
    this.statoSquadre.set('caricamento');
    this.service.squadre(societa.id).subscribe({
      next: (squadre) => {
        this.squadre.set(squadre);
        this.statoSquadre.set('completata');
      },
      error: () => this.statoSquadre.set('errore'),
    });
  }

  /**
   * Le partite si abbinano al campo per l'id dell'impianto in presenze: un
   * campo che non ce l'ha (da un file, o inserito a mano) non le chiede.
   * Se presenze non risponde il backend manda un elenco vuoto, e la scheda
   * resta com'è.
   */
  private caricaPartite(societa: Societa): void {
    this.partite.set([]);
    if (!societa.anagraficaImpiantoId) {
      this.statoPartite.set('completata');
      return;
    }
    this.statoPartite.set('caricamento');
    this.service.partite(societa.id).subscribe({
      next: (partite) => {
        this.partite.set(partite);
        this.statoPartite.set('completata');
      },
      error: () => this.statoPartite.set('errore'),
    });
  }
}
