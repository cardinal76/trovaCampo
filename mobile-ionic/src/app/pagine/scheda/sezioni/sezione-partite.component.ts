import { Component, input } from '@angular/core';
import { IonBadge, IonIcon, IonSpinner } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { timeOutline } from 'ionicons/icons';
import { StemmaComponent } from '../../../componenti/stemma/stemma.component';
import {
  GIORNI_PARTITE_SCHEDA,
  Partita,
  dataPartita,
  punteggioLive,
  quandoPartita,
} from '../../../modelli/partita';
import { paginaCampionato, paginaGirone } from '../../../presenze';
import { Stato } from './stati';

/**
 * Le prossime partite su questo campo, una riga per partita, con il giorno in
 * un riquadro da calendario. Il caricamento sta nella pagina, che passa lo stato.
 */
@Component({
  selector: 'div[schedaPartite]',
  imports: [IonBadge, IonIcon, IonSpinner, StemmaComponent],
  templateUrl: './sezione-partite.component.html',
  styleUrl: './sezione-partite.component.scss',
})
export class SezionePartiteComponent {
  readonly stato = input.required<Stato>();
  readonly partite = input.required<Partita[]>();

  readonly giorniPartite = GIORNI_PARTITE_SCHEDA;
  readonly quandoPartita = quandoPartita;
  readonly dataPartita = (partita: Partita) => dataPartita(partita);
  readonly punteggioLive = punteggioLive;
  // Le pagine del Comitato su presenze, dove portano campionato e girone.
  readonly paginaCampionato = paginaCampionato;
  readonly paginaGirone = paginaGirone;

  constructor() {
    addIcons({ timeOutline });
  }
}
