import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  InfiniteScrollCustomEvent,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInfiniteScroll,
  IonInfiniteScrollContent,
  IonItem,
  IonLabel,
  IonList,
  IonIcon,
  IonSearchbar,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  checkmarkCircle,
  footballOutline,
  locationOutline,
  navigateCircleOutline,
  shieldOutline,
} from 'ionicons/icons';
import { StemmaComponent } from '../../componenti/stemma/stemma.component';
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';
import {
  TUTTE,
  nellaProvincia,
  opzioniProvincia,
  provinciaValida,
  testoProvincia,
} from '../../modelli/provincia';
import {
  Societa,
  haCoordinate,
  indirizzoCompleto,
  nomeCompleto,
  senzaPosizionePrecisa,
} from '../../modelli/societa';
import { amministratoreRicordato } from '../../servizi/amministratore-ricordato';
import { CampiCambiatiService } from '../../servizi/campi-cambiati.service';
import { ProvinciaSceltaService } from '../../servizi/provincia-scelta.service';
import { SocietaService } from '../../servizi/societa.service';

/** Quante righe si aggiungono ogni volta che si arriva in fondo. */
const BLOCCO = 100;

/** Minuscolo e senza accenti: "Città" e "citta" devono trovarsi a vicenda. */
function normalizza(testo: string): string {
  return testo
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase();
}

/**
 * Tutti i campi in ordine di nome della società, con un filtro istantaneo.
 *
 * Il filtro lavora sul telefono, non sul server: l'elenco si scarica una
 * volta sola e da lì ogni lettera digitata è immediata. Le righe si mostrano
 * a blocchi mentre si scorre, perché qualche migliaio di elementi Ionic in
 * pagina tutti insieme renderebbe lo scorrimento pesante.
 *
 * Chi amministra ha in più l'interruttore "Solo senza posizione precisa":
 * lascia le società senza coordinate o col segnaposto approssimato, da
 * aprire e correggere con la modifica. Lavora sugli stessi dati già
 * scaricati, quindi anche lui è istantaneo. Allo stesso modo "Senza stemma"
 * lascia le società a cui manca ancora lo stemma.
 *
 * Il filtro per provincia è per tutti, e la scelta è la stessa della mappa
 * (vedi {@link ProvinciaSceltaService}). Tutti i filtri si sommano.
 */
@Component({
  selector: 'pagina-elenco',
  imports: [
    RouterLink,
    MenuUtenteComponent,
    StemmaComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonIcon,
    IonHeader,
    IonInfiniteScroll,
    IonInfiniteScrollContent,
    IonItem,
    IonLabel,
    IonList,
    IonSearchbar,
    IonSelect,
    IonSelectOption,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './elenco.page.html',
  styleUrl: './elenco.page.scss',
})
export class ElencoPage {
  private readonly service = inject(SocietaService);
  private readonly provinciaScelta = inject(ProvinciaSceltaService);
  private readonly cambiati = inject(CampiCambiatiService);
  /** La versione dell'archivio dei campi scaricati: se al rientro è salita, si ricarica. */
  private versioneCaricata = -1;

  readonly stato = signal<'caricamento' | 'pronto' | 'errore'>('caricamento');
  readonly campi = signal<Societa[]>([]);
  readonly filtro = signal('');
  readonly mostrati = signal(BLOCCO);

  /** Come nella scheda: l'interruttore solo se su questo browser è entrato un amministratore. */
  readonly amministratore = signal(amministratoreRicordato());
  readonly soloSenzaPosizione = signal(false);
  readonly soloSenzaStemma = signal(false);

  readonly nomeCompleto = nomeCompleto;
  readonly indirizzoCompleto = indirizzoCompleto;
  readonly haCoordinate = haCoordinate;
  readonly TUTTE = TUTTE;

  /**
   * Letto insieme ad {@link amministratore}: se chi amministra esce, il
   * filtro rimasto acceso non deve nascondere campi a chi non vede
   * l'interruttore per spegnerlo.
   */
  readonly filtroPosizione = computed(() => this.amministratore() && this.soloSenzaPosizione());
  /** Come {@link filtroPosizione}, per le società ancora senza stemma. */
  readonly filtroStemma = computed(() => this.amministratore() && this.soloSenzaStemma());

  /** Le province dei campi scaricati, con "Tutte" in testa. */
  readonly opzioniProvincia = computed(() => opzioniProvincia(this.campi()));
  /** Sulla pillola chiusa: "Tutte" da solo non direbbe di cosa. */
  readonly testoProvincia = computed(() => testoProvincia(this.provincia(), this.opzioniProvincia()));
  readonly provincia = computed(() =>
    provinciaValida(this.provinciaScelta.scelta(), this.opzioniProvincia()),
  );

  /** Il conteggio "N di M" serve quando qualche filtro toglie righe. */
  readonly filtrato = computed(
    () =>
      this.filtro() !== '' ||
      this.provincia() !== TUTTE ||
      this.filtroPosizione() ||
      this.filtroStemma(),
  );

  /** Il testo su cui cerca il filtro, calcolato una volta sola per campo. */
  private readonly indice = computed(() =>
    this.campi().map((campo) => ({
      campo,
      testo: normalizza(
        [nomeCompleto(campo), campo.nomeImpianto, indirizzoCompleto(campo)].join(' '),
      ),
    })),
  );

  readonly filtrati = computed(() => {
    const parole = normalizza(this.filtro()).split(/\s+/).filter(Boolean);
    const soloSenzaPosizione = this.filtroPosizione();
    const soloSenzaStemma = this.filtroStemma();
    const provincia = this.provincia();
    return this.indice()
      .filter(({ campo }) => nellaProvincia(campo, provincia))
      .filter(({ campo }) => !soloSenzaPosizione || senzaPosizionePrecisa(campo))
      .filter(({ campo }) => !soloSenzaStemma || !campo.logoUrl)
      .filter(({ testo }) => parole.every((parola) => testo.includes(parola)))
      .map(({ campo }) => campo);
  });

  readonly visibili = computed(() => this.filtrati().slice(0, this.mostrati()));

  constructor() {
    addIcons({
      checkmarkCircle,
      footballOutline,
      locationOutline,
      navigateCircleOutline,
      shieldOutline,
    });
    this.carica();
  }

  /** A ogni ingresso: si può essere appena entrati o usciti dal menu utente. */
  ionViewWillEnter(): void {
    this.amministratore.set(amministratoreRicordato());
    // Di ritorno da una scheda eliminata, modificata o creata: l'elenco
    // deve mostrarla com'è adesso (o non mostrarla più).
    if (this.cambiati.versione() !== this.versioneCaricata) {
      this.carica();
    }
  }

  /**
   * Scarica i campi. Se ce ne sono già, li sostituisce senza passare dallo
   * spinner: filtro, provincia, righe mostrate e scorrimento restano dove
   * erano, e le righe (tracciate per id) non si ridisegnano. Se il nuovo
   * scaricamento fallisce restano i campi di prima, e si riprova al
   * prossimo rientro.
   */
  carica(): void {
    const giaPronto = this.stato() === 'pronto';
    this.versioneCaricata = this.cambiati.versione();
    if (!giaPronto) {
      this.stato.set('caricamento');
    }
    this.service.tutti().subscribe({
      next: (campi) => {
        this.campi.set(campi);
        this.stato.set('pronto');
      },
      error: () => {
        this.versioneCaricata = -1;
        if (!giaPronto) {
          this.stato.set('errore');
        }
      },
    });
  }

  cambiaFiltro(valore: string | null | undefined): void {
    this.filtro.set(valore ?? '');
    this.mostrati.set(BLOCCO);
  }

  cambiaProvincia(provincia: string): void {
    this.provinciaScelta.scegli(provincia);
    this.mostrati.set(BLOCCO);
  }

  cambiaSoloSenzaPosizione(attivo: boolean): void {
    this.soloSenzaPosizione.set(attivo);
    this.mostrati.set(BLOCCO);
  }

  cambiaSoloSenzaStemma(attivo: boolean): void {
    this.soloSenzaStemma.set(attivo);
    this.mostrati.set(BLOCCO);
  }

  altri(evento: InfiniteScrollCustomEvent): void {
    this.mostrati.update((n) => n + BLOCCO);
    void evento.target.complete();
  }
}
