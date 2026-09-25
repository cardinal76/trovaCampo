import { Component, computed, inject, input, signal } from '@angular/core';
import { IonButton, IonIcon, IonSpinner } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { locate, navigateOutline } from 'ionicons/icons';
import { linkPercorso, suggerimentoPartenza } from '../../modelli/percorso';
import { Posizione } from '../../modelli/vicini';
import { DiagnosiService } from '../../servizi/diagnosi.service';
import { RichiestaPosizione } from '../../servizi/richiesta-posizione';
import { RiquadroDiagnosiComponent } from '../riquadro-diagnosi/riquadro-diagnosi.component';

/**
 * "Partenza" e "Percorso" sotto la mappa di un campo: il pulsante apre il
 * navigatore (Google Maps) con le indicazioni fino al campo.
 *
 * La partenza vuota vuol dire "La mia posizione": se l'utente l'ha chiesta
 * con il pulsante accanto, il link porta le sue coordinate; se no lascia
 * che sia Google a prenderla dal dispositivo. Chi scrive un indirizzo parte
 * da lì. La posizione non si chiede da sola all'apertura della scheda: il
 * permesso compare solo a chi tocca il pulsante.
 *
 * La Mappa ha lo stesso blocco nel popup di ogni campo, scritto a mano
 * perché il popup è HTML di Leaflet; le parole e il link sono gli stessi
 * ({@link linkPercorso}, {@link suggerimentoPartenza}).
 */
@Component({
  selector: 'percorso-campo',
  imports: [IonButton, IonIcon, IonSpinner, RiquadroDiagnosiComponent],
  template: `
    <label class="partenza">
      <span class="etichetta">Partenza</span>
      <span class="riga">
        <input
          type="text"
          name="partenza"
          autocomplete="street-address"
          enterkeyhint="go"
          [placeholder]="suggerimento()"
          [value]="indirizzo()"
          (input)="scrivi($any($event.target).value)"
          (keydown.enter)="apri.click()"
        />
        <ion-button
          fill="outline"
          class="usa-posizione"
          aria-label="Parti dalla mia posizione"
          title="Parti dalla mia posizione"
          [disabled]="richiesta.inCorso()"
          (click)="usaPosizione()"
        >
          @if (richiesta.inCorso()) {
            <ion-spinner slot="icon-only" name="crescent"></ion-spinner>
          } @else {
            <ion-icon slot="icon-only" name="locate"></ion-icon>
          }
        </ion-button>
      </span>
    </label>
    @if (richiesta.inCorso()) {
      <p class="nota" aria-live="polite">Cerco dove sei…</p>
    } @else if (dallaPosizione()) {
      <p class="nota" aria-live="polite">Parti dalla tua posizione attuale.</p>
    }
    @if (richiesta.riquadro(); as istruzioni) {
      <riquadro-diagnosi
        class="diagnosi"
        role="alert"
        [istruzioni]="istruzioni"
        [occupato]="richiesta.inCorso()"
        [chiudibile]="true"
        (riprova)="usaPosizione()"
        (chiudi)="richiesta.errore.set(null)"
      ></riquadro-diagnosi>
    } @else if (richiesta.errore(); as errore) {
      <p class="nota errore" role="alert">{{ errore }}</p>
    }
    <!-- Un link vero e non window.open: si apre dentro il tocco, e il blocco dei popup non lo ferma. -->
    <a #apri class="apri-percorso" [href]="link()" target="_blank" rel="noopener">
      <ion-icon name="navigate-outline" aria-hidden="true"></ion-icon>
      Percorso
    </a>
  `,
  styles: `
    :host {
      display: block;
      margin: 0 1rem 0.75rem;
    }

    .partenza {
      display: block;
    }

    .etichetta {
      display: block;
      margin-bottom: 0.25rem;
      font-size: 0.875rem;
      color: var(--ion-color-medium, #666);
    }

    .riga {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }

    /* 16px: Safari su iPhone non ingrandisce la pagina quando lo si tocca. */
    input {
      flex: 1 1 auto;
      min-width: 0;
      min-height: 44px;
      box-sizing: border-box;
      padding: 0 0.75rem;
      border: 1px solid var(--ion-color-medium, #92949c);
      border-radius: 8px;
      background: var(--ion-background-color, #fff);
      color: var(--ion-text-color, #000);
      font: inherit;
      font-size: 16px;
    }

    .usa-posizione {
      flex: 0 0 auto;
      width: 44px;
      height: 44px;
      margin: 0;
      --padding-start: 0;
      --padding-end: 0;
      --border-radius: 8px;
    }

    .nota {
      margin: 0.35rem 0 0;
      font-size: 0.85rem;
      color: var(--ion-color-medium, #666);
    }

    .nota.errore {
      color: var(--ion-color-danger, #c5000f);
    }

    .diagnosi {
      margin-top: 0.5rem;
    }

    .apri-percorso {
      display: flex;
      gap: 0.4rem;
      align-items: center;
      justify-content: center;
      min-height: 44px;
      margin-top: 0.5rem;
      border-radius: 8px;
      background: var(--ion-color-primary, #3880ff);
      color: var(--ion-color-primary-contrast, #fff);
      font-weight: 600;
      text-decoration: none;
    }
  `,
})
export class PercorsoCampoComponent {
  private readonly diagnosi = inject(DiagnosiService);

  /** Dove si va: le coordinate del campo. */
  readonly destinazione = input.required<Posizione>();

  readonly richiesta = new RichiestaPosizione();
  readonly indirizzo = signal('');
  /** La posizione avuta con il pulsante; null finché non la si chiede. */
  readonly posizione = signal<Posizione | null>(null);

  readonly suggerimento = computed(() => suggerimentoPartenza(this.diagnosi.posizione()));
  readonly dallaPosizione = computed(
    () => this.posizione() !== null && this.indirizzo().trim() === '',
  );
  readonly link = computed(() =>
    linkPercorso(this.destinazione(), this.indirizzo(), this.posizione()),
  );

  constructor() {
    addIcons({ locate, navigateOutline });
  }

  scrivi(testo: string): void {
    this.indirizzo.set(testo);
  }

  /** Svuota l'indirizzo e chiede la posizione: se arriva, si parte da lì. */
  async usaPosizione(): Promise<void> {
    this.indirizzo.set('');
    const posizione = await this.richiesta.chiedi();
    if (posizione) {
      this.posizione.set(posizione);
    }
  }
}
