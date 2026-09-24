import { Component, inject, signal, viewChild } from '@angular/core';
import {
  IonButton,
  IonContent,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonPopover,
  NavController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, cloudUpload, globe, logIn, logOut, personCircle } from 'ionicons/icons';
import { SITO_FOOTBALLER } from '../../footballer';
import { amministratoreRicordato, nomeRicordato } from '../../servizi/amministratore-ricordato';

let prossimo = 0;

/**
 * Icona utente in alto a destra: apre un menu con "Accedi" oppure, per chi è
 * entrato con il ruolo trovacampo-admin, con le funzioni di amministrazione e
 * "Esci". In fondo, per tutti, "Torna a footballer.it".
 *
 * Non carica keycloak-js: chi è entrato lo sa da {@link amministratoreRicordato},
 * riletto ogni volta che il menu si apre. Il login vero lo fanno /accedi e le
 * pagine di amministrazione.
 */
@Component({
  selector: 'menu-utente',
  imports: [IonButton, IonContent, IonIcon, IonItem, IonLabel, IonList, IonPopover],
  template: `
    <ion-button [id]="id" aria-label="Utente">
      <ion-icon slot="icon-only" name="person-circle"></ion-icon>
    </ion-button>
    <ion-popover [trigger]="id" (willPresent)="rileggi()">
      <ng-template>
        <ion-content>
          <ion-list lines="full">
            @if (amministratore()) {
              @if (nome(); as chi) {
                <ion-item>
                  <ion-label>
                    <p>Entrato come</p>
                    <h3>{{ chi }}</h3>
                  </ion-label>
                </ion-item>
              }
              <ion-item button [detail]="false" (click)="vai('/admin/societa/nuova')">
                <ion-icon slot="start" name="add"></ion-icon>
                <ion-label>Nuova società</ion-label>
              </ion-item>
              <ion-item button [detail]="false" (click)="vai('/admin/importazione')">
                <ion-icon slot="start" name="cloud-upload"></ion-icon>
                <ion-label>Importa campi</ion-label>
              </ion-item>
              <ion-item button [detail]="false" (click)="vai('/accedi?esci=1')">
                <ion-icon slot="start" name="log-out" color="danger"></ion-icon>
                <ion-label color="danger">Esci</ion-label>
              </ion-item>
            } @else {
              <ion-item button [detail]="false" (click)="vai('/accedi')">
                <ion-icon slot="start" name="log-in"></ion-icon>
                <ion-label>Accedi</ion-label>
              </ion-item>
            }
            <!-- Il sito della casa, per chiunque, entrato o no. Un link vero e
                 non una navigazione del router: si esce dall'app. Nella stessa
                 scheda, perché è un "tornare" e non un "aprire accanto": sul
                 telefono una scheda nuova lascerebbe dietro una TrovaCampo
                 che nessuno chiude. -->
            <ion-item class="footballer" [href]="sitoFootballer" [detail]="false" lines="none">
              <ion-icon slot="start" name="globe" aria-hidden="true"></ion-icon>
              <ion-label>Torna a footballer.it</ion-label>
            </ion-item>
          </ion-list>
        </ion-content>
      </ng-template>
    </ion-popover>
  `,
})
export class MenuUtenteComponent {
  private readonly navigazione = inject(NavController);
  private readonly popover = viewChild.required(IonPopover);

  /** Il trigger del popover va per id: ogni pagina ha la sua icona. */
  readonly id = `menu-utente-${prossimo++}`;
  readonly amministratore = signal(amministratoreRicordato());
  readonly nome = signal(nomeRicordato());
  readonly sitoFootballer = SITO_FOOTBALLER;

  constructor() {
    addIcons({ add, cloudUpload, globe, logIn, logOut, personCircle });
  }

  rileggi(): void {
    this.amministratore.set(amministratoreRicordato());
    this.nome.set(nomeRicordato());
  }

  async vai(indirizzo: string): Promise<void> {
    await this.popover().dismiss();
    await this.navigazione.navigateForward(indirizzo);
  }
}
