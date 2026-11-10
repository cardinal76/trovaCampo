import { Component, computed, input } from '@angular/core';
import { IonItem, IonLabel, IonList, IonNote } from '@ionic/angular/standalone';
import { Societa } from '../../../modelli/societa';

/**
 * Anagrafica della società: matricola, presidente, sede e contatti.
 *
 * Il host è il {@code div slot="content"} dell'accordion, che la pagina
 * dichiara: {@code ion-accordion-group} riconosce solo gli accordion figli
 * diretti, quindi l'accordion e la sua intestazione restano nella pagina.
 */
@Component({
  selector: 'div[schedaAnagrafica]',
  imports: [IonList, IonItem, IonLabel, IonNote],
  templateUrl: './sezione-anagrafica.component.html',
  styleUrl: './sezione-anagrafica.component.scss',
})
export class SezioneAnagraficaComponent {
  readonly societa = input.required<Societa>();
  /** Se la società ha squadre nei comunicati: cambia il motivo per cui mancano i contatti. */
  readonly conSquadre = input(false);

  /**
   * Presidente, sede e contatti non li scrivono i comunicati: mancano anche
   * alle società che vengono dall'anagrafica di presenze, che a mano non sono
   * state inserite. Il motivo cambia, e la frase con lui.
   */
  readonly testoSenzaAnagrafica = computed(() =>
    this.societa().anagraficaSocietaId || this.conSquadre()
      ? 'Presidente, sede e contatti non sono ancora stati inseriti.'
      : 'Anagrafica non ancora disponibile per questo campo (inserito manualmente).',
  );

  readonly haAnagrafica = computed(() => {
    const societa = this.societa();
    return Boolean(
      societa.presidente ||
      societa.indirizzoSede ||
      societa.telefono ||
      societa.email ||
      societa.sitoWeb ||
      societa.matricola,
    );
  });

  readonly prezziScuolaCalcio = computed(() => {
    const societa = this.societa();

    if (societa.scuolaCalcio === undefined) {
      return null;
    }

    if (!societa.scuolaCalcio) {
      return 'No';
    }

    return societa.prezziScuolaCalcio ? `Sì — ${societa.prezziScuolaCalcio}` : 'Sì';
  });
}
