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
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  IonBackButton,
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
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { create } from 'ionicons/icons';
import * as L from 'leaflet';
import {
  Societa,
  SocietaGeolocalizzata,
  haCoordinate,
  indirizzoCompleto,
  nomeCompleto,
} from '../../modelli/societa';
import { Squadra, dettaglioSquadra } from '../../modelli/squadra';
import { iconaCampo } from '../../mappa/icona-campo';
import { amministratoreRicordato } from '../../servizi/amministratore-ricordato';
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
    IonBackButton,
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

  private readonly contenitoreMappa = viewChild<ElementRef<HTMLDivElement>>('contenitoreMappa');
  private mappa: L.Map | null = null;
  private segnaposto: L.Marker | null = null;

  readonly id = this.rotta.snapshot.paramMap.get('id') ?? '';
  readonly societa = signal<Societa | null>(null);
  readonly stato = signal<Stato>('caricamento');
  /** Le squadre dall'anagrafica di presenze: si caricano a parte, e possono mancare. */
  readonly squadre = signal<Squadra[]>([]);
  readonly statoSquadre = signal<Stato | 'assenti'>('assenti');
  readonly dettaglioSquadra = dettaglioSquadra;
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
    addIcons({ create });

    // La mappa nasce quando il suo contenitore compare (cioè a dati caricati e
    // solo se il campo ha una posizione) e si riallinea se i dati cambiano,
    // per esempio tornando qui dopo aver corretto l'indirizzo.
    effect(() => {
      const contenitore = this.contenitoreMappa();
      const campo = this.campoGeolocalizzato();

      if (contenitore && campo) {
        this.disegnaMappa(contenitore.nativeElement, campo);
      }
    });
  }

  ngOnDestroy(): void {
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
   * Solo per le società che vengono dall'anagrafica di presenze. Se presenze
   * non risponde la scheda resta com'è, con un avviso al posto delle squadre.
   */
  private caricaSquadre(societa: Societa): void {
    if (!societa.anagraficaSocietaId) {
      this.squadre.set([]);
      this.statoSquadre.set('assenti');
      return;
    }
    this.statoSquadre.set('caricamento');
    this.service.squadre(societa.id).subscribe({
      next: (squadre) => {
        this.squadre.set(squadre);
        this.statoSquadre.set('completata');
      },
      error: () => this.statoSquadre.set('errore'),
    });
  }
}
