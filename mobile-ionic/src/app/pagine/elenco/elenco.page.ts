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
  IonListHeader,
  IonNote,
  IonSearchbar,
  IonSpinner,
  IonTitle,
  IonToggle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';
import {
  Societa,
  haCoordinate,
  indirizzoCompleto,
  nomeCompleto,
  senzaPosizionePrecisa,
} from '../../modelli/societa';
import { amministratoreRicordato } from '../../servizi/amministratore-ricordato';
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
 * scaricati, quindi anche lui è istantaneo.
 */
@Component({
  selector: 'pagina-elenco',
  imports: [
    RouterLink,
    MenuUtenteComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonInfiniteScroll,
    IonInfiniteScrollContent,
    IonItem,
    IonLabel,
    IonList,
    IonListHeader,
    IonNote,
    IonSearchbar,
    IonSpinner,
    IonTitle,
    IonToggle,
    IonToolbar,
  ],
  templateUrl: './elenco.page.html',
  styleUrl: './elenco.page.scss',
})
export class ElencoPage {
  private readonly service = inject(SocietaService);

  readonly stato = signal<'caricamento' | 'pronto' | 'errore'>('caricamento');
  readonly campi = signal<Societa[]>([]);
  readonly filtro = signal('');
  readonly mostrati = signal(BLOCCO);

  /** Come nella scheda: l'interruttore solo se su questo browser è entrato un amministratore. */
  readonly amministratore = signal(amministratoreRicordato());
  readonly soloSenzaPosizione = signal(false);

  readonly nomeCompleto = nomeCompleto;
  readonly indirizzoCompleto = indirizzoCompleto;
  readonly haCoordinate = haCoordinate;

  /**
   * Letto insieme ad {@link amministratore}: se chi amministra esce, il
   * filtro rimasto acceso non deve nascondere campi a chi non vede
   * l'interruttore per spegnerlo.
   */
  readonly filtroPosizione = computed(() => this.amministratore() && this.soloSenzaPosizione());

  /** Il conteggio "N di M" serve quando qualche filtro toglie righe. */
  readonly filtrato = computed(() => this.filtro() !== '' || this.filtroPosizione());

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
    return this.indice()
      .filter(({ campo }) => !soloSenzaPosizione || senzaPosizionePrecisa(campo))
      .filter(({ testo }) => parole.every((parola) => testo.includes(parola)))
      .map(({ campo }) => campo);
  });

  readonly visibili = computed(() => this.filtrati().slice(0, this.mostrati()));

  constructor() {
    this.carica();
  }

  /** A ogni ingresso: si può essere appena entrati o usciti dal menu utente. */
  ionViewWillEnter(): void {
    this.amministratore.set(amministratoreRicordato());
  }

  carica(): void {
    this.stato.set('caricamento');
    this.service.tutti().subscribe({
      next: (campi) => {
        this.campi.set(campi);
        this.stato.set('pronto');
      },
      error: () => this.stato.set('errore'),
    });
  }

  cambiaFiltro(valore: string | null | undefined): void {
    this.filtro.set(valore ?? '');
    this.mostrati.set(BLOCCO);
  }

  cambiaSoloSenzaPosizione(attivo: boolean): void {
    this.soloSenzaPosizione.set(attivo);
    this.mostrati.set(BLOCCO);
  }

  altri(evento: InfiniteScrollCustomEvent): void {
    this.mostrati.update((n) => n + BLOCCO);
    void evento.target.complete();
  }
}
