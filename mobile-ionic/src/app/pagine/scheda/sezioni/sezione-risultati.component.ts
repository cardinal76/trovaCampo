import { Component, input } from '@angular/core';
import { IonSpinner } from '@ionic/angular/standalone';
import {
  Risultato,
  dettaglioRisultato,
  giornoRisultato,
  punteggio,
  risultatoPerEsteso,
  siglaEsito,
} from '../../../modelli/risultato';
import { paginaCampionato, paginaGirone } from '../../../presenze';
import { StatoSezioneRisultati } from './stati';

/**
 * Gli ultimi risultati: l'ultima partita giocata da ogni squadra. Si chiedono
 * al backend solo quando la piega si apre: lo stato lo tiene la pagina.
 */
@Component({
  selector: 'div[schedaRisultati]',
  imports: [IonSpinner],
  templateUrl: './sezione-risultati.component.html',
  styleUrl: './sezione-risultati.component.scss',
})
export class SezioneRisultatiComponent {
  readonly stato = input.required<StatoSezioneRisultati>();
  readonly risultati = input.required<Risultato[]>();

  readonly giornoRisultato = giornoRisultato;
  readonly punteggio = punteggio;
  readonly siglaEsito = siglaEsito;
  readonly risultatoPerEsteso = risultatoPerEsteso;
  readonly dettaglioRisultato = dettaglioRisultato;
  // Le pagine del Comitato su presenze, dove portano campionato e girone.
  readonly paginaCampionato = paginaCampionato;
  readonly paginaGirone = paginaGirone;
}
