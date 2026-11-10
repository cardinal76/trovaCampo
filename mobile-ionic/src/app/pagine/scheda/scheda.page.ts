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
  IonSpinner,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  calendarOutline,
  create,
  footballOutline,
  trash,
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
  Partita,
  RILETTURA_IN_DIRETTA_MS,
  campionatoPartita,
  daSeguire,
  fondiLive,
  squadrePartita,
} from '../../modelli/partita';
import { AccessoSocieta } from '../../presenze';
import { Risultato } from '../../modelli/risultato';
import { Squadra, dettaglioSquadra } from '../../modelli/squadra';
import { PercorsoCampoComponent } from '../../componenti/percorso-campo/percorso-campo.component';
import { AccessoClubComponent } from '../../componenti/accesso-club/accesso-club.component';
import { StemmaComponent } from '../../componenti/stemma/stemma.component';
import { iconaCampo } from '../../mappa/icona-campo';
import {
  EliminazioneSocietaService,
  leggiEliminaDopoAccesso,
  scriviEliminaDopoAccesso,
} from '../../servizi/amministrazione/eliminazione-societa.service';
import { amministratoreRicordato } from '../../servizi/amministrazione/amministratore-ricordato';
import { NotificheService } from '../../servizi/notifiche/notifiche.service';
import { SocietaService } from '../../servizi/societa/societa.service';
import { SezioneAnagraficaComponent } from './sezioni/sezione-anagrafica.component';
import { SezioneCampionatiComponent } from './sezioni/sezione-campionati.component';
import { SezionePartiteComponent } from './sezioni/sezione-partite.component';
import { SezioneRisultatiComponent } from './sezioni/sezione-risultati.component';
import { Stato, StatoSezioneRisultati } from './sezioni/stati';

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
    SezioneAnagraficaComponent,
    SezioneCampionatiComponent,
    SezionePartiteComponent,
    SezioneRisultatiComponent,
    RouterLink,
    PercorsoCampoComponent,
    StemmaComponent,
    AccessoClubComponent,
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
  /**
   * Come si entra in Footballer da questo club, se ne è cliente: si chiede a
   * presenze all'apertura della scheda. Vuoto (nessun collegamento) per le
   * altre società, se presenze non risponde o non manda ancora il campo.
   */
  readonly accesso = signal<AccessoSocieta | null>(null);
  /**
   * Le prossime partite su questo campo, dal calendario di presenze: anche
   * loro a parte, e solo per un campo che viene da lì.
   */
  readonly partite = signal<Partita[]>([]);
  readonly statoPartite = signal<Stato>('caricamento');
  /**
   * Gli ultimi risultati della società, dal più recente: si chiedono solo
   * quando qualcuno apre la sezione (vedi {@link StatoSezioneRisultati}).
   */
  readonly risultati = signal<Risultato[]>([]);
  readonly statoRisultati = signal<StatoSezioneRisultati>('chiusa');
  /**
   * Se la piega dei risultati è aperta in questo momento. All'arrivo nessuna
   * lo è, ma tornando qui dalla modifica la pagina è la stessa di prima, con
   * le pieghe come le aveva lasciate: i risultati vanno richiesti, non
   * lasciati in attesa per sempre.
   */
  private readonly risultatiInVista = signal(false);
  readonly squadrePartita = squadrePartita;
  readonly campionatoPartita = campionatoPartita;
  /**
   * Ogni minuto, se una partita si può star giocando, si chiedono i soli
   * punteggi in diretta e cambiano solo quelli: la scheda non si ricarica.
   */
  private rilettura: ReturnType<typeof setInterval> | null = null;
  /** Il pulsante "Modifica": solo se su questo browser è entrato un amministratore. */
  readonly amministratore = signal(amministratoreRicordato());
  private readonly eliminaSocieta = inject(EliminazioneSocietaService);
  /** Mentre l'eliminazione è in corso il pulsante è spento: niente doppi tocchi. */
  readonly eliminazione = this.eliminaSocieta.inCorso;

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

  constructor() {
    addIcons({
      calendarOutline,
      create,
      footballOutline,
      notifications,
      trash,
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

  /**
   * Una piega si è aperta o chiusa. Gli ultimi risultati si chiedono qui, la
   * prima volta che la loro piega si apre: una chiamata per apertura, non una
   * per visita alla scheda.
   *
   * Riaprendola dopo un errore si riprova, perché la sezione ha appena detto
   * «riprova tra poco»; dopo una risposta riuscita no, i risultati di una
   * domenica non cambiano mentre si guarda la pagina.
   */
  pieghe(evento: Event): void {
    const valore = (evento as CustomEvent<{ value?: string | string[] | null }>).detail?.value;
    const aperte = Array.isArray(valore) ? valore : valore ? [valore] : [];
    this.risultatiInVista.set(aperte.includes('risultati'));
    if (this.risultatiInVista() && this.statoRisultati() !== 'completata') {
      this.caricaRisultati();
    }
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
      await this.avvisa(
        'Segui già 30 squadre: smetti di seguirne una dalla pagina Notifiche.',
        true,
      );
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
    this.fermaRilettura();
    this.rimuoviMappa();
  }

  private fermaRilettura(): void {
    if (this.rilettura !== null) {
      clearInterval(this.rilettura);
      this.rilettura = null;
    }
  }

  /** I punteggi in diretta delle partite che si possono star giocando, e solo quelli. */
  private aggiornaPunteggi(): void {
    const ids = daSeguire(this.partite());
    if (ids.length === 0 || document.visibilityState === 'hidden') {
      return;
    }
    this.service.live(ids).subscribe({
      next: (punteggi) => this.partite.update((partite) => fondiLive(partite, ids, punteggi)),
      // Un errore lascia i punteggi di prima: al minuto dopo si riprova.
      error: () => undefined,
    });
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
    const eliminaDopoAccesso = leggiEliminaDopoAccesso() === this.id;
    if (eliminaDopoAccesso) {
      scriviEliminaDopoAccesso(null);
    }
    this.service.perId(this.id).subscribe({
      next: (dati) => {
        this.societa.set(dati);
        this.stato.set('completata');
        this.caricaSquadre(dati);
        this.caricaAccesso(dati);
        this.caricaPartite(dati);
        this.rileggiRisultati();
        if (eliminaDopoAccesso) {
          void this.eliminaSocieta.riprendi(dati);
        }
      },
      error: () => this.stato.set('errore'),
    });
  }

  /** Il pulsante cestino, per chi amministra (vedi {@link EliminazioneSocietaService}). */
  async elimina(): Promise<void> {
    const societa = this.societa();
    if (societa) {
      await this.eliminaSocieta.elimina(societa);
    }
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
  private caricaAccesso(societa: Societa): void {
    this.accesso.set(null);
    this.service.accesso(societa.id).subscribe((accesso) => this.accesso.set(accesso));
  }

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
    this.fermaRilettura();
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
        // Fuori dalla zona di Angular: un timer che gira sempre non deve
        // tenere la pagina «occupata». Si rientra solo per aggiornare.
        this.zona.runOutsideAngular(() => {
          this.rilettura = setInterval(
            () => this.zona.run(() => this.aggiornaPunteggi()),
            RILETTURA_IN_DIRETTA_MS,
          );
        });
      },
      error: () => this.statoPartite.set('errore'),
    });
  }

  /**
   * All'arrivo sulla scheda i risultati di prima non valgono più. Si
   * richiedono solo se la loro piega è rimasta aperta (si torna dalla
   * modifica); altrimenti si aspetta che qualcuno la apra.
   */
  private rileggiRisultati(): void {
    this.risultati.set([]);
    this.statoRisultati.set('chiusa');
    if (this.risultatiInVista()) {
      this.caricaRisultati();
    }
  }

  /**
   * Gli ultimi risultati della società. Il backend risponde sempre 200 e dice
   * nello stato se i risultati ci sono, se presenze non li ha o se non ha
   * risposto: la sezione lo racconta a parole, e la pagina non si rompe
   * nemmeno quando l'endpoint di presenze non c'è ancora.
   */
  private caricaRisultati(): void {
    this.statoRisultati.set('caricamento');
    this.service.risultati(this.id).subscribe({
      next: (risposta) => {
        this.risultati.set(risposta.stato === 'DISPONIBILI' ? risposta.risultati : []);
        this.statoRisultati.set(
          risposta.stato === 'DISPONIBILI'
            ? 'completata'
            : risposta.stato === 'NON_DISPONIBILI'
              ? 'assenti'
              : 'errore',
        );
      },
      // Il backend di TrovaCampo non risponde: per chi guarda è lo stesso
      // caso di presenze giù, e si dice allo stesso modo.
      error: () => {
        this.risultati.set([]);
        this.statoRisultati.set('errore');
      },
    });
  }
}
