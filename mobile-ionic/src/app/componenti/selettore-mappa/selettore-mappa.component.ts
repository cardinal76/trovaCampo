import {
  Component,
  ElementRef,
  Input,
  NgZone,
  OnDestroy,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonNote,
  IonTitle,
  IonToolbar,
  ModalController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { close } from 'ionicons/icons';
import * as L from 'leaflet';

/** Centro e zoom di partenza quando il campo non ha ancora una posizione. */
const CENTRO_ITALIA: L.LatLngTuple = [41.9, 12.5];
const ZOOM_ITALIA = 5.5;
const ZOOM_POSIZIONE = 16;

/**
 * Mappa a schermo intero per scegliere le coordinate del campo, aperta come
 * ion-modal dalla pagina di modifica: un tocco sulla mappa mette (o sposta)
 * un segnaposto trascinabile, "Conferma posizione" chiude la modale con le
 * coordinate scelte.
 *
 * Si chiude da sola (come menu-utente col suo popover), così chi la apre deve
 * solo leggere il risultato da onDidDismiss().
 */
@Component({
  selector: 'selettore-mappa',
  imports: [
    DecimalPipe,
    IonButton,
    IonButtons,
    IonContent,
    IonFooter,
    IonHeader,
    IonIcon,
    IonNote,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './selettore-mappa.component.html',
  styleUrl: './selettore-mappa.component.scss',
})
export class SelettoreMappaComponent implements OnDestroy {
  private readonly modaleControllo = inject(ModalController);
  private readonly zona = inject(NgZone);

  /** Posizione di partenza, se il campo ne ha già una. */
  @Input() lat?: number;
  @Input() lng?: number;

  private readonly contenitore = viewChild<ElementRef<HTMLElement>>('contenitoreMappa');
  private mappa: L.Map | null = null;
  private segnaposto: L.Marker | null = null;

  readonly posizione = signal<{ lat: number; lng: number } | null>(null);

  constructor() {
    addIcons({ close });

    // Il contenitore esiste solo a modale aperta: la mappa si disegna appena c'è.
    effect(() => {
      const contenitore = this.contenitore()?.nativeElement;
      if (contenitore && !this.mappa) {
        this.disegna(contenitore);
      }
    });
  }

  ngOnDestroy(): void {
    this.mappa?.remove();
    this.mappa = null;
  }

  annulla(): void {
    void this.modaleControllo.dismiss(null, 'cancel');
  }

  conferma(): void {
    const posizione = this.posizione();
    if (posizione) {
      void this.modaleControllo.dismiss(posizione, 'confirm');
    }
  }

  private disegna(contenitore: HTMLElement): void {
    const partenza =
      typeof this.lat === 'number' && typeof this.lng === 'number'
        ? L.latLng(this.lat, this.lng)
        : null;

    this.zona.runOutsideAngular(() => {
      const mappa = L.map(contenitore);
      this.mappa = mappa;

      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap',
      }).addTo(mappa);

      if (partenza) {
        mappa.setView(partenza, ZOOM_POSIZIONE);
        this.posiziona(partenza);
      } else {
        mappa.setView(CENTRO_ITALIA, ZOOM_ITALIA);
      }

      mappa.on('click', (evento: L.LeafletMouseEvent) => this.posiziona(evento.latlng));

      // Le dimensioni definitive arrivano solo a modale aperta finita.
      setTimeout(() => mappa.invalidateSize(), 200);
    });
  }

  /** Crea il segnaposto al primo tocco, altrimenti lo sposta lì. Sempre fuori dalla zona di Angular. */
  private posiziona(punto: L.LatLng): void {
    if (this.segnaposto) {
      this.segnaposto.setLatLng(punto);
    } else {
      this.segnaposto = L.marker(punto, { draggable: true }).addTo(this.mappa!);
      this.segnaposto.on('dragend', () => this.aggiorna(this.segnaposto!.getLatLng()));
    }
    this.aggiorna(punto);
  }

  private aggiorna(punto: L.LatLng): void {
    this.zona.run(() => this.posizione.set({ lat: punto.lat, lng: punto.lng }));
  }
}
