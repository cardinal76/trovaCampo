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
import { Router, RouterLink } from '@angular/router';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
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
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';
import { SocietaService } from '../../servizi/societa.service';
import { ZOOM_ICONE, aggiornaNomiCampi, iconaCampo } from '../../mappa/icona-campo';

/** L'Italia intera, finché non ci sono campi da inquadrare. */
const ITALIA = L.latLngBounds([36.6, 6.6], [47.1, 18.5]);

/**
 * Tutti i campi con una posizione, su una mappa sola.
 *
 * I punti sono cerchi disegnati su canvas e non segnaposto HTML come nella
 * pagina dei risultati: con qualche migliaio di campi, un elemento del DOM
 * per ciascuno renderebbe lento ogni spostamento della mappa, mentre il
 * canvas li ridisegna tutti in un colpo.
 *
 * Solo da vicino (da ZOOM_ICONE) i cerchi lasciano il posto
 * all'icona di un campo, e solo per i campi dentro la porzione visibile:
 * a quello zoom sono pochi, quindi gli elementi del DOM restano gestibili.
 * Da ZOOM_NOMI in su l'icona mostra anche il nome della società.
 */
@Component({
  selector: 'pagina-mappa',
  imports: [
    RouterLink,
    MenuUtenteComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './mappa.page.html',
  styleUrl: './mappa.page.scss',
})
export class MappaPage implements OnDestroy {
  private readonly service = inject(SocietaService);
  private readonly router = inject(Router);
  private readonly zona = inject(NgZone);

  private readonly contenitore = viewChild<ElementRef<HTMLElement>>('contenitoreMappa');
  private mappa: L.Map | null = null;
  private cerchi: L.LayerGroup | null = null;
  private icone: L.LayerGroup | null = null;
  private readonly segnaposto = new Map<string, L.Marker>();

  readonly stato = signal<'caricamento' | 'pronto' | 'errore'>('caricamento');
  readonly campi = signal<Societa[]>([]);
  readonly geolocalizzati = computed(() => this.campi().filter(haCoordinate));
  readonly senzaPosizione = computed(
    () => this.campi().length - this.geolocalizzati().length,
  );

  constructor() {
    this.carica();

    // Il contenitore esiste solo dopo che i dati sono arrivati: la mappa si
    // disegna quando ci sono entrambi.
    effect(() => {
      const contenitore = this.contenitore()?.nativeElement;
      if (contenitore && this.stato() === 'pronto') {
        this.disegna(contenitore, this.geolocalizzati());
      }
    });
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

  ngOnDestroy(): void {
    this.mappa?.remove();
    this.mappa = null;
    this.segnaposto.clear();
  }

  private disegna(contenitore: HTMLElement, campi: SocietaGeolocalizzata[]): void {
    this.zona.runOutsideAngular(() => {
      this.mappa?.remove();
      const mappa = L.map(contenitore, { preferCanvas: true });
      this.mappa = mappa;

      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap',
      }).addTo(mappa);

      const colore =
        getComputedStyle(document.documentElement).getPropertyValue('--ion-color-primary').trim() ||
        '#2f6fce';

      const cerchi = L.layerGroup();
      for (const campo of campi) {
        this.conDettagli(
          L.circleMarker([campo.lat, campo.lng], {
            radius: 6,
            color: '#fff',
            weight: 1.5,
            fillColor: colore,
            fillOpacity: 0.9,
          }),
          campo,
        ).addTo(cerchi);
      }
      this.cerchi = cerchi;
      this.icone = L.layerGroup();
      this.segnaposto.clear();

      mappa.on('zoomend moveend', () => this.aggiornaSegnaposto(mappa, campi));

      if (campi.length > 0) {
        mappa.fitBounds(L.latLngBounds(campi.map((c) => L.latLng(c.lat, c.lng))), {
          padding: [30, 30],
          maxZoom: 15,
        });
      } else {
        mappa.fitBounds(ITALIA);
      }
      this.aggiornaSegnaposto(mappa, campi);

      mappa.on('popupopen', (evento: L.PopupEvent) => {
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

      // Le dimensioni definitive arrivano solo a transizione di pagina finita.
      setTimeout(() => mappa.invalidateSize(), 200);
    });
  }

  /**
   * Cerchi da lontano, icone da vicino. Le icone si creano solo per i campi
   * visibili (con un po' di margine) e si riusano tra uno spostamento e
   * l'altro.
   */
  private aggiornaSegnaposto(mappa: L.Map, campi: SocietaGeolocalizzata[]): void {
    const cerchi = this.cerchi;
    const icone = this.icone;
    if (!cerchi || !icone) {
      return;
    }

    aggiornaNomiCampi(mappa);
    if (mappa.getZoom() < ZOOM_ICONE) {
      icone.remove();
      cerchi.addTo(mappa);
      return;
    }
    cerchi.remove();
    icone.addTo(mappa);

    const visibili = mappa.getBounds().pad(0.3);
    for (const campo of campi) {
      const dentro = visibili.contains([campo.lat, campo.lng]);
      let segnaposto = this.segnaposto.get(campo.id);
      if (dentro && !segnaposto) {
        segnaposto = this.conDettagli(
          L.marker([campo.lat, campo.lng], { icon: iconaCampo(campo) }),
          campo,
        );
        this.segnaposto.set(campo.id, segnaposto);
      }
      if (!segnaposto) {
        continue;
      }
      if (dentro) {
        icone.addLayer(segnaposto);
      } else if (!segnaposto.isPopupOpen()) {
        icone.removeLayer(segnaposto);
      }
    }
  }

  private conDettagli<T extends L.Layer>(livello: T, campo: SocietaGeolocalizzata): T {
    return livello
      .bindTooltip(testoSicuro(nomeCompleto(campo)))
      .bindPopup(this.popup(campo));
  }

  private popup(campo: SocietaGeolocalizzata): string {
    return `
      <strong>${testoSicuro(nomeCompleto(campo))}</strong><br />
      ${testoSicuro(campo.nomeImpianto)}<br />
      ${testoSicuro(indirizzoCompleto(campo))}<br />
      <button type="button" class="collegamento-scheda" data-societa="${testoSicuro(campo.id)}">
        Vedi scheda società ›
      </button>
    `;
  }
}
