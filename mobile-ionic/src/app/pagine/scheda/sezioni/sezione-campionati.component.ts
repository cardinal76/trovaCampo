import { Component, computed, input, output } from '@angular/core';
import {
  IonButton,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonNote,
  IonSpinner,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { notifications, notificationsOutline } from 'ionicons/icons';
import { Campionato } from '../../../modelli/societa';
import {
  Squadra,
  aggiornamentoRisultati,
  dettaglioSquadra,
  portaleNonRaggiungibile,
} from '../../../modelli/squadra';
import { Stato } from './stati';

/**
 * Le squadre della società con il campionato di ognuna, e i campionati dei
 * comunicati. La campanella segue o smette di seguire una squadra, ma a farlo
 * è la pagina: qui si emette solo la squadra toccata.
 */
@Component({
  selector: 'div[schedaCampionati]',
  imports: [IonButton, IonIcon, IonItem, IonLabel, IonList, IonListHeader, IonNote, IonSpinner],
  templateUrl: './sezione-campionati.component.html',
  styleUrl: './sezione-campionati.component.scss',
})
export class SezioneCampionatiComponent {
  readonly stato = input.required<Stato>();
  readonly squadre = input.required<Squadra[]>();
  readonly campionati = input.required<Campionato[]>();
  readonly agonistica = input.required<Campionato[]>();
  readonly scuolaCalcio = input.required<Campionato[]>();
  /** Il campo della società: una squadra che gioca altrove lo dice. */
  readonly nomeImpianto = input.required<string>();
  /** Le chiavi delle squadre seguite: la campanella cambia appena le si tocca. */
  readonly chiaviSeguite = input.required<ReadonlySet<string>>();

  readonly cambiaSegui = output<Squadra>();

  readonly dettaglioSquadra = dettaglioSquadra;
  readonly aggiornamentoRisultati = aggiornamentoRisultati;

  /**
   * Il portale LND non risponde a presenze: i risultati arrivano solo dai
   * Comunicati Ufficiali, e chi guarda nel fine settimana deve sapere perché
   * sono fermi.
   */
  readonly portaleNonRaggiungibile = computed(() => portaleNonRaggiungibile(this.squadre()));

  constructor() {
    addIcons({ notifications, notificationsOutline });
  }

  seguita(squadra: Squadra): boolean {
    return !!squadra.chiave && this.chiaviSeguite().has(squadra.chiave);
  }
}
