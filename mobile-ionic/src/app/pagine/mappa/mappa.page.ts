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
  IonSelect,
  IonSelectOption,
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
import { nellaProvincia, opzioniProvincia, provinciaValida } from '../../modelli/provincia';
import { ProvinciaSceltaService } from '../../servizi/provincia-scelta.service';
import { SocietaService } from '../../servizi/societa.service';
import {
  ZOOM_ICONE,
  aggiornaNomiCampi,
  campoSuCanvas,
  iconaCampo,
  immagineCampo,
} from '../../mappa/icona-campo';

/** L'Italia intera, finché non ci sono campi da inquadrare. */
const ITALIA = L.latLngBounds([36.6, 6.6], [47.1, 18.5]);

/**
 * Tutti i campi con una posizione, su una mappa sola.
 *
 * Ogni campo ha l'icona di un campo da calcio a tutti gli zoom. Da lontano è
 * disegnata sul canvas e non come segnaposto HTML: con qualche migliaio di
 * campi, un elemento del DOM per ciascuno renderebbe lento ogni spostamento
 * della mappa, mentre il canvas li ridisegna tutti in un colpo.
 *
 * Da vicino (da ZOOM_ICONE) la stessa icona diventa un elemento HTML, solo
 * per i campi dentro la porzione visibile: a quello zoom sono pochi, e da
 * ZOOM_NOMI in su l'icona mostra anche il nome della società.
 *
 * Il filtro per provincia è lo stesso dell'elenco (stessa scelta, vedi
 * {@link ProvinciaSceltaService}): cambia i campi sulla mappa e la vista si
 * stringe su quelli rimasti, senza ricreare la mappa.
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
    IonSelect,
    IonSelectOption,
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
  private readonly provinciaScelta = inject(ProvinciaSceltaService);

  private readonly contenitore = viewChild<ElementRef<HTMLElement>>('contenitoreMappa');
  private mappa: L.Map | null = null;
  private sulCanvas: L.LayerGroup | null = null;
  private icone: L.LayerGroup | null = null;
  private readonly segnaposto = new Map<string, L.Marker>();
  /** I campi sulla mappa adesso, quelli che guarda {@link aggiornaSegnaposto}. */
  private mostrati: SocietaGeolocalizzata[] = [];

  readonly stato = signal<'caricamento' | 'pronto' | 'errore'>('caricamento');
  readonly campi = signal<Societa[]>([]);

  /** Le province dei campi scaricati, con "Tutte" in testa. */
  readonly opzioniProvincia = computed(() => opzioniProvincia(this.campi()));
  readonly provincia = computed(() =>
    provinciaValida(this.provinciaScelta.scelta(), this.opzioniProvincia()),
  );

  /** I campi della provincia scelta: anche il conteggio "senza posizione" parla di loro. */
  readonly campiNellaProvincia = computed(() => {
    const provincia = this.provincia();
    return this.campi().filter((campo) => nellaProvincia(campo, provincia));
  });
  readonly geolocalizzati = computed(() => this.campiNellaProvincia().filter(haCoordinate));
  readonly senzaPosizione = computed(
    () => this.campiNellaProvincia().length - this.geolocalizzati().length,
  );

  constructor() {
    this.carica();

    // Il contenitore esiste solo dopo che i dati sono arrivati: la mappa si
    // crea quando ci sono entrambi, e dopo, a ogni cambio di provincia, si
    // rifanno solo i segnaposto.
    effect(() => {
      const contenitore = this.contenitore()?.nativeElement;
      if (contenitore && this.stato() === 'pronto') {
        const campi = this.geolocalizzati();
        // L'icona per il canvas si carica una volta sola, di solito subito.
        immagineCampo().then((immagine) => {
          if (this.contenitore()?.nativeElement !== contenitore) {
            return;
          }
          if (this.mappa?.getContainer() !== contenitore) {
            this.creaMappa(contenitore);
          }
          this.mostra(campi, immagine);
        });
      }
    });
  }

  cambiaProvincia(provincia: string): void {
    this.provinciaScelta.scegli(provincia);
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

  private creaMappa(contenitore: HTMLElement): void {
    this.zona.runOutsideAngular(() => {
      this.mappa?.remove();
      const mappa = L.map(contenitore, { preferCanvas: true });
      this.mappa = mappa;
      this.sulCanvas = null;
      this.icone = null;

      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap',
      }).addTo(mappa);

      mappa.on('zoomend moveend', () => this.aggiornaSegnaposto(mappa));

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
      // Se nel frattempo la pagina è stata lasciata la mappa non c'è più, e
      // Leaflet su una mappa tolta si rompe.
      setTimeout(() => {
        if (this.mappa === mappa) {
          mappa.invalidateSize();
        }
      }, 200);
    });
  }

  /**
   * Mette sulla mappa questi campi al posto di quelli di prima, e porta la
   * vista su di loro.
   */
  private mostra(campi: SocietaGeolocalizzata[], immagine: HTMLImageElement): void {
    const mappa = this.mappa;
    if (!mappa) {
      return;
    }
    this.zona.runOutsideAngular(() => {
      this.sulCanvas?.remove();
      this.icone?.remove();

      const sulCanvas = L.layerGroup();
      for (const campo of campi) {
        this.conDettagli(campoSuCanvas([campo.lat, campo.lng], immagine), campo).addTo(sulCanvas);
      }
      this.sulCanvas = sulCanvas;
      this.icone = L.layerGroup();
      this.segnaposto.clear();
      this.mostrati = campi;

      if (campi.length > 0) {
        mappa.fitBounds(L.latLngBounds(campi.map((c) => L.latLng(c.lat, c.lng))), {
          padding: [30, 30],
          maxZoom: 15,
        });
      } else {
        mappa.fitBounds(ITALIA);
      }
      this.aggiornaSegnaposto(mappa);
    });
  }

  /**
   * Icone sul canvas da lontano, icone HTML da vicino. Queste ultime si
   * creano solo per i campi visibili (con un po' di margine) e si riusano
   * tra uno spostamento e l'altro.
   */
  private aggiornaSegnaposto(mappa: L.Map): void {
    const sulCanvas = this.sulCanvas;
    const icone = this.icone;
    if (!sulCanvas || !icone) {
      return;
    }

    aggiornaNomiCampi(mappa);
    if (mappa.getZoom() < ZOOM_ICONE) {
      icone.remove();
      sulCanvas.addTo(mappa);
      return;
    }
    sulCanvas.remove();
    icone.addTo(mappa);

    const visibili = mappa.getBounds().pad(0.3);
    for (const campo of this.mostrati) {
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
