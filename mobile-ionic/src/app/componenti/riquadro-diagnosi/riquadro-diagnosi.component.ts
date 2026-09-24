import { Component, input, output } from '@angular/core';
import { IonButton, IonIcon, IonSpinner } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  closeOutline,
  constructOutline,
  eyeOffOutline,
  locationOutline,
  lockOpenOutline,
  navigateOutline,
  notificationsOffOutline,
  openOutline,
  refreshOutline,
  shareOutline,
  timeOutline,
} from 'ionicons/icons';
import { Istruzioni } from '../../servizi/istruzioni';

/**
 * "Cosa blocca e come sbloccarlo": il titolo, due righe e i passi numerati
 * per il dispositivo in mano, con "Riprova" che rifà il controllo (e, dove
 * si può, la richiesta). Lo usano la pagina Notifiche e "Vicino a me" della
 * Mappa, così le parole sono le stesse ovunque.
 */
@Component({
  selector: 'riquadro-diagnosi',
  imports: [IonButton, IonIcon, IonSpinner],
  template: `
    @let dati = istruzioni();
    <div class="riquadro" [attr.data-caso]="dati.caso">
      <ion-icon class="icona" [name]="dati.icona" aria-hidden="true"></ion-icon>
      <div class="corpo">
        <p class="titolo">{{ dati.titolo }}</p>
        <p class="spiegazione">{{ dati.spiegazione }}</p>
        @if (dati.passi.length) {
          <ol class="passi">
            @for (passo of dati.passi; track $index) {
              <li>{{ passo }}</li>
            }
          </ol>
        }
        @if (dati.nota) {
          <p class="nota">{{ dati.nota }}</p>
        }
        @if (dati.riprova || chiudibile()) {
          <div class="azioni">
            @if (dati.riprova) {
              <ion-button
                size="small"
                class="riprova"
                [disabled]="occupato()"
                (click)="riprova.emit()"
              >
                @if (occupato()) {
                  <ion-spinner slot="start" name="crescent"></ion-spinner>
                } @else {
                  <ion-icon slot="start" name="refresh-outline"></ion-icon>
                }
                Riprova
              </ion-button>
            }
            @if (chiudibile()) {
              <ion-button size="small" fill="clear" class="chiudi" (click)="chiudi.emit()">
                <ion-icon slot="start" name="close-outline"></ion-icon>
                Chiudi
              </ion-button>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .riquadro {
      display: flex;
      gap: 0.75rem;
      align-items: flex-start;
      padding: 0.8rem 0.9rem;
      border-radius: 12px;
      border-left: 4px solid var(--ion-color-warning, #ffc409);
      background: rgba(var(--ion-color-warning-rgb, 255, 196, 9), 0.14);
      font-size: 0.9rem;
      line-height: 1.4;
    }

    .icona {
      flex: 0 0 auto;
      margin-top: 0.1rem;
      color: var(--ion-color-warning-shade, #e0ac08);
      font-size: 1.4rem;
    }

    .corpo {
      flex: 1;
      min-width: 0;
    }

    p {
      margin: 0;
    }

    .titolo {
      font-weight: 600;
    }

    .spiegazione {
      margin-top: 0.25rem;
    }

    .passi {
      margin: 0.5rem 0 0;
      padding-left: 1.3rem;

      li + li {
        margin-top: 0.35rem;
      }

      li::marker {
        font-weight: 600;
      }
    }

    .nota {
      margin-top: 0.5rem;
      color: var(--ion-color-medium, #666);
      font-size: 0.8rem;
    }

    .azioni {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem;
      margin-top: 0.6rem;

      ion-button {
        margin: 0;
      }

      ion-spinner {
        width: 1rem;
        height: 1rem;
        margin-right: 0.4rem;
      }
    }
  `,
})
export class RiquadroDiagnosiComponent {
  readonly istruzioni = input.required<Istruzioni>();
  readonly occupato = input(false);
  /** Con "Chiudi": sulla Mappa il riquadro copre un pezzo di mappa. */
  readonly chiudibile = input(false);
  readonly riprova = output<void>();
  readonly chiudi = output<void>();

  constructor() {
    addIcons({
      alertCircleOutline,
      closeOutline,
      constructOutline,
      eyeOffOutline,
      locationOutline,
      lockOpenOutline,
      navigateOutline,
      notificationsOffOutline,
      openOutline,
      refreshOutline,
      shareOutline,
      timeOutline,
    });
  }
}
