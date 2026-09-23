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
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
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
import * as L from 'leaflet';
import {
  Societa,
  SocietaGeolocalizzata,
  haCoordinate,
  indirizzoCompleto,
  nomeCompleto,
  testoSicuro,
} from '../../modelli/societa';
import { SocietaService } from '../../servizi/societa.service';
import { ZOOM_ICONE, aggiornaNomiCampi, iconaCampo } from '../../mappa/icona-campo';

type Stato = 'caricamento' | 'completata' | 'errore';

/** Zoom usato quando c'è un solo campo da inquadrare. */
const ZOOM_SINGOLO = 15;

/**
 * Seconda schermata della Funzione 1: la mappa dei campi trovati (mockup
 * grafica/FunzioneUnoSchermataDue.jpg) con sotto l'elenco completo dei
 * risultati. La barra in alto permette di correggere la ricerca sul posto.
 */
@Component({
  selector: 'pagina-risultati',
  imports: [
    FormsModule,
    IonBackButton,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
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
  templateUrl: './risultati.page.html',
  styleUrl: './risultati.page.scss',
})
export class RisultatiPage implements OnDestroy {
  private readonly rotta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(SocietaService);
  private readonly zona = inject(NgZone);

  readonly termine = signal('');
  readonly testo = signal('');
  readonly stato = signal<Stato>('caricamento');
  readonly risultati = signal<Societa[]>([]);
  readonly geolocalizzati = computed(() => this.risultati().filter(haCoordinate));

  private readonly contenitoreMappa =
    viewChild<ElementRef<HTMLDivElement>>('contenitoreMappa');

  private mappa?: L.Map;
  private pin?: L.LayerGroup;
  private segnaposto: { marker: L.Marker; campo: SocietaGeolocalizzata }[] = [];

  readonly nomeCompleto = nomeCompleto;
  readonly indirizzoCompleto = indirizzoCompleto;

  constructor() {
    this.rotta.queryParamMap.pipe(takeUntilDestroyed()).subscribe((parametri) => {
      const query = (parametri.get('q') ?? '').trim();
      this.termine.set(query);
      this.testo.set(query);
      this.cerca();
    });

    // Crea la mappa quando il suo contenitore compare nel DOM e riallinea i
    // pin a ogni nuovo risultato.
    effect(() => {
      const contenitore = this.contenitoreMappa();
      const campi = this.geolocalizzati();

      if (!contenitore || campi.length === 0) {
        return;
      }

      this.disegnaMappa(contenitore.nativeElement, campi);
    });
  }

  ngOnDestroy(): void {
    this.mappa?.remove();
    this.mappa = undefined;
  }

  cerca(): void {
    const query = this.termine();

    if (query.length === 0) {
      this.risultati.set([]);
      this.stato.set('completata');
      return;
    }

    this.stato.set('caricamento');

    this.service.cerca(query).subscribe({
      next: (dati) => {
        this.risultati.set(dati);
        this.stato.set('completata');
      },
      error: () => {
        this.risultati.set([]);
        this.stato.set('errore');
      },
    });
  }

  nuovaRicerca(): void {
    const query = this.testo().trim();

    if (query.length === 0 || query === this.termine()) {
      return;
    }

    this.router.navigate([], {
      relativeTo: this.rotta,
      queryParams: { q: query },
    });
  }

  apriScheda(societa: Societa): void {
    this.router.navigate(['/societa', societa.id]);
  }

  vaiAdAggiungi(): void {
    this.router.navigate(['/aggiungi']);
  }

  private disegnaMappa(contenitore: HTMLElement, campi: SocietaGeolocalizzata[]): void {
    // Leaflet lavora sul DOM: fuori dalla zona Angular per non far girare il
    // rilevamento delle modifiche a ogni evento di pan e zoom.
    this.zona.runOutsideAngular(() => {
      if (!this.mappa) {
        this.mappa = L.map(contenitore, { attributionControl: true });
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution: '© OpenStreetMap',
        }).addTo(this.mappa);
        this.pin = L.layerGroup().addTo(this.mappa);
        this.mappa.on('zoomend', () => this.aggiornaIcone());
      }

      this.pin?.clearLayers();

      this.segnaposto = campi.map((campo) => ({
        campo,
        marker: L.marker([campo.lat, campo.lng], { icon: this.icona(), title: nomeCompleto(campo) })
          .addTo(this.pin!)
          .bindPopup(this.contenutoPopup(campo)),
      }));

      if (campi.length === 1) {
        this.mappa.setView([campi[0].lat, campi[0].lng], ZOOM_SINGOLO);
      } else {
        this.mappa.fitBounds(L.latLngBounds(campi.map((c) => L.latLng(c.lat, c.lng))), {
          padding: [40, 40],
        });
      }

      this.aggiornaIcone();

      // Il contenitore prende le sue dimensioni definitive solo a
      // transizione di pagina conclusa.
      setTimeout(() => this.mappa?.invalidateSize(), 200);
    });

    this.collegaPopupAllaScheda();
  }

  /**
   * Come nella pagina Mappa: pin da lontano, icona di un campo da calcio da
   * ZOOM_ICONE in su e, più vicino ancora, il nome della società.
   */
  private aggiornaIcone(): void {
    const mappa = this.mappa;
    if (!mappa) {
      return;
    }
    aggiornaNomiCampi(mappa);
    const vicino = mappa.getZoom() >= ZOOM_ICONE;
    for (const { marker, campo } of this.segnaposto) {
      marker.setIcon(vicino ? iconaCampo(campo) : this.icona());
    }
  }

  /** Pin disegnato in CSS: evita le immagini di Leaflet, che i bundler non risolvono. */
  private icona(): L.DivIcon {
    return L.divIcon({
      className: 'pin-campo',
      iconSize: [22, 22],
      iconAnchor: [11, 22],
      popupAnchor: [0, -20],
    });
  }

  private contenutoPopup(campo: SocietaGeolocalizzata): string {
    return `
      <strong>${testoSicuro(nomeCompleto(campo))}</strong><br />
      ${testoSicuro(campo.nomeImpianto)}<br />
      ${testoSicuro(indirizzoCompleto(campo))}<br />
      <button type="button" class="collegamento-scheda" data-societa="${testoSicuro(campo.id)}">
        Vedi scheda società ›
      </button>
    `;
  }

  private collegaPopupAllaScheda(): void {
    this.mappa?.off('popupopen');
    this.mappa?.on('popupopen', (evento: L.PopupEvent) => {
      const bottone = evento.popup
        .getElement()
        ?.querySelector<HTMLButtonElement>('[data-societa]');

      bottone?.addEventListener('click', () => {
        const id = bottone.dataset['societa'];
        if (id) {
          this.zona.run(() => this.router.navigate(['/societa', id]));
        }
      });
    });
  }
}
