import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  InfiniteScrollCustomEvent,
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
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
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { map } from 'ionicons/icons';
import { Societa, indirizzoCompleto, nomeCompleto } from '../../modelli/societa';
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
 */
@Component({
  selector: 'pagina-elenco',
  imports: [
    RouterLink,
    IonBackButton,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
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

  readonly nomeCompleto = nomeCompleto;
  readonly indirizzoCompleto = indirizzoCompleto;

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
    return this.indice()
      .filter(({ testo }) => parole.every((parola) => testo.includes(parola)))
      .map(({ campo }) => campo);
  });

  readonly visibili = computed(() => this.filtrati().slice(0, this.mostrati()));

  constructor() {
    addIcons({ map });
    this.carica();
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

  altri(evento: InfiniteScrollCustomEvent): void {
    this.mostrati.update((n) => n + BLOCCO);
    void evento.target.complete();
  }
}
