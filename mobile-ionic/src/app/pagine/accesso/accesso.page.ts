import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
  NavController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { logOut, warning } from 'ionicons/icons';
import {
  AutenticazioneService,
  RUOLO_AMMINISTRATORE,
} from '../../servizi/autenticazione.service';

/**
 * Login dalla voce "Accedi" del menu utente, sul Keycloak di presenze.
 *
 * Chi ha il ruolo trovacampo-admin torna subito alla home: nel menu utente
 * ora trova le funzioni di amministrazione. Chi non ce l'ha resta qui e vede
 * con che utente è entrato e quali ruoli ha davvero nel token: con lo stesso
 * Keycloak di presenze capita di entrare in silenzio con un altro account già
 * aperto nel browser. Con /accedi?esci=1 (la voce "Esci" del menu utente) si esce.
 */
@Component({
  selector: 'pagina-accesso',
  imports: [
    RouterLink,
    IonBackButton,
    IonButton,
    IonButtons,
    IonCard,
    IonCardContent,
    IonContent,
    IonHeader,
    IonIcon,
    IonItem,
    IonLabel,
    IonSpinner,
    IonText,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './accesso.page.html',
  styleUrl: './accesso.page.scss',
})
export class AccessoPage {
  private readonly navigazione = inject(NavController);
  readonly autenticazione = inject(AutenticazioneService);
  readonly ruolo = RUOLO_AMMINISTRATORE;

  private readonly uscita = inject(ActivatedRoute).snapshot.queryParamMap.has('esci');

  readonly stato = signal<'in-corso' | 'entrato' | 'errore'>('in-corso');

  constructor() {
    addIcons({ logOut, warning });
    this.entra();
  }

  entra(): void {
    this.stato.set('in-corso');
    this.autenticazione.accedi().then(
      () => {
        if (this.uscita) {
          this.esci();
        } else if (this.autenticazione.amministratore()) {
          void this.navigazione.navigateRoot('/');
        } else {
          this.stato.set('entrato');
        }
      },
      () => this.stato.set('errore'),
    );
  }

  esci(): void {
    void this.autenticazione.esci();
  }
}
