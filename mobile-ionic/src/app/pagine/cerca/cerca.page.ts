import {
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { globeOutline, searchOutline } from 'ionicons/icons';
import * as L from 'leaflet';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { Societa, haCoordinate, indirizzoCompleto, nomeCompleto } from '../../modelli/societa';
import { ZOOM_ICONE, iconaCampo } from '../../mappa/icona-campo';
import { SITO_FOOTBALLER } from '../../footballer';
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';
import { SocietaService } from '../../servizi/societa.service';

/** Centro dello sfondo: Roma, piazza Venezia. */
const ROMA = L.latLng(41.8964, 12.4823);

/** Lettere da scrivere prima che compaiano i suggerimenti. */
const MINIMO_SUGGERIMENTI = 3;
/** Suggerimenti mostrati sotto la casella; gli altri si vedono con "Vai". */
const MASSIMO_SUGGERIMENTI = 6;

/**
 * Prima schermata della Funzione 1 (Ricerca campo): titolo, casella di
 * ricerca e pulsante "Vai", come nei mockup grafica/home.jpg e
 * grafica/FunzioneUnoSchermataUno.jpg.
 *
 * Sullo sfondo c'è la mappa di Roma, ferma e schiarita da un velo, allo zoom
 * delle icone (ZOOM_ICONE): i campi si vedono come nella pagina Mappa. Se
 * i campi non arrivano resta la sola mappa.
 */
@Component({
  selector: 'pagina-cerca',
  imports: [
    FormsModule,
    RouterLink,
    MenuUtenteComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
    IonInput,
    IonItem,
    IonLabel,
    IonList,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './cerca.page.html',
  styleUrl: './cerca.page.scss',
})
export class CercaPage implements OnDestroy {
  private readonly router = inject(Router);
  private readonly service = inject(SocietaService);
  private readonly zona = inject(NgZone);

  private readonly sfondo = viewChild.required<ElementRef<HTMLElement>>('sfondoMappa');
  private mappa: L.Map | null = null;

  readonly testo = signal('');
  readonly pronto = computed(() => this.testo().trim().length > 0);

  /** Suggerimenti per il testo scritto; null finché non ce n'è da mostrare. */
  readonly suggerimenti = signal<Societa[] | null>(null);
  readonly caricamentoSuggerimenti = signal(false);
  readonly altriSuggerimenti = signal(0);

  readonly nomeCompleto = nomeCompleto;
  readonly indirizzoCompleto = indirizzoCompleto;
  readonly sitoFootballer = SITO_FOOTBALLER;

  constructor() {
    addIcons({ globeOutline, searchOutline });

    toObservable(this.testo)
      .pipe(
        map((testo) => testo.trim()),
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((termine) => {
          if (termine.length < MINIMO_SUGGERIMENTI) {
            this.caricamentoSuggerimenti.set(false);
            return of(null);
          }
          this.caricamentoSuggerimenti.set(true);
          // Un errore non blocca nulla: con "Vai" si arriva ai risultati.
          return this.service.cerca(termine).pipe(catchError(() => of(null)));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((trovati) => {
        this.caricamentoSuggerimenti.set(false);
        this.suggerimenti.set(trovati?.slice(0, MASSIMO_SUGGERIMENTI) ?? null);
        this.altriSuggerimenti.set(Math.max(0, (trovati?.length ?? 0) - MASSIMO_SUGGERIMENTI));
      });

    afterNextRender(() => this.disegnaSfondo(this.sfondo().nativeElement));
  }

  ngOnDestroy(): void {
    this.mappa?.remove();
    this.mappa = null;
  }

  cerca(): void {
    const termine = this.testo().trim();

    if (termine.length === 0) {
      return;
    }

    this.router.navigate(['/risultati'], { queryParams: { q: termine } });
  }

  apriScheda(societa: Societa): void {
    this.router.navigate(['/societa', societa.id]);
  }

  /** Mappa decorativa: niente trascinamento, zoom o controlli. */
  private disegnaSfondo(contenitore: HTMLElement): void {
    this.zona.runOutsideAngular(() => {
      const mappa = L.map(contenitore, {
        preferCanvas: true,
        zoomControl: false,
        attributionControl: true,
        dragging: false,
        touchZoom: false,
        doubleClickZoom: false,
        scrollWheelZoom: false,
        boxZoom: false,
        keyboard: false,
      });
      this.mappa = mappa;
      mappa.setView(ROMA, ZOOM_ICONE);

      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap',
      }).addTo(mappa);

      // Le dimensioni definitive arrivano solo a transizione di pagina finita.
      setTimeout(() => mappa.invalidateSize(), 200);
    });

    this.service.tutti().subscribe({
      next: (campi) => {
        const mappa = this.mappa;
        const geolocalizzati = campi.filter(haCoordinate);
        if (!mappa || geolocalizzati.length === 0) {
          return;
        }

        this.zona.runOutsideAngular(() => {
          // Solo i campi attorno alla zona inquadrata: a questo zoom sono pochi.
          const visibili = mappa.getBounds().pad(0.5);
          for (const campo of geolocalizzati) {
            if (visibili.contains([campo.lat, campo.lng])) {
              L.marker([campo.lat, campo.lng], {
                icon: iconaCampo(campo),
                interactive: false,
                keyboard: false,
              }).addTo(mappa);
            }
          }
        });
      },
      // Lo sfondo è solo decorazione: senza campi resta la sola mappa.
      error: () => undefined,
    });
  }
}
