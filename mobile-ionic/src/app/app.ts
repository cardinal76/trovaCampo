import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import {
  IonApp,
  IonIcon,
  IonLabel,
  IonRouterOutlet,
  IonTabBar,
  IonTabButton,
  NavController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { list, map, notifications, search } from 'ionicons/icons';
import { NotificheService } from './servizi/notifiche.service';

type Scheda = 'cerca' | 'campi' | 'mappa' | 'notifiche';

/** La scheda a cui appartiene un indirizzo, se è una delle quattro. */
function schedaDi(url: string): Scheda | null {
  const percorso = url.split(/[?#]/)[0];
  if (percorso === '/' || percorso.startsWith('/risultati')) {
    return 'cerca';
  }
  if (percorso.startsWith('/campi')) {
    return 'campi';
  }
  if (percorso.startsWith('/mappa')) {
    return 'mappa';
  }
  if (percorso.startsWith('/notifiche')) {
    return 'notifiche';
  }
  return null;
}

/**
 * Guscio dell'app: le pagine sopra, le quattro schede Cerca, Elenco, Mappa
 * e Notifiche sempre in basso.
 *
 * Non è un ion-tabs: con ion-tabs ogni scheda avrebbe la sua pila e i suoi
 * indirizzi (/cerca/societa/…), e i link già in giro a /societa/:id
 * cambierebbero. Qui la barra è solo navigazione: toccare una scheda riparte
 * da capo su quella pagina. Sulle pagine che non sono di nessuna scheda (una
 * scheda società, l'amministrazione) resta evidenziata l'ultima.
 */
@Component({
  selector: 'app-root',
  imports: [IonApp, IonIcon, IonLabel, IonRouterOutlet, IonTabBar, IonTabButton],
  template: `
    <ion-app>
      <div class="guscio">
        <ion-router-outlet></ion-router-outlet>
        <ion-tab-bar [selectedTab]="scheda()">
          <ion-tab-button tab="cerca" (click)="vai('/')">
            <ion-icon name="search"></ion-icon>
            <ion-label>Cerca</ion-label>
          </ion-tab-button>
          <ion-tab-button tab="campi" (click)="vai('/campi')">
            <ion-icon name="list"></ion-icon>
            <ion-label>Elenco</ion-label>
          </ion-tab-button>
          <ion-tab-button tab="mappa" (click)="vai('/mappa')">
            <ion-icon name="map"></ion-icon>
            <ion-label>Mappa</ion-label>
          </ion-tab-button>
          <ion-tab-button tab="notifiche" (click)="vai('/notifiche')">
            <ion-icon name="notifications"></ion-icon>
            <ion-label>Notifiche</ion-label>
          </ion-tab-button>
        </ion-tab-bar>
      </div>
    </ion-app>
  `,
  styles: `
    .guscio {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
    }

    ion-router-outlet {
      position: relative;
      flex: 1;
      contain: layout size style;
    }
  `,
})
export class App {
  private readonly router = inject(Router);
  private readonly navigazione = inject(NavController);
  private readonly notifiche = inject(NotificheService);

  readonly scheda = signal<Scheda>('cerca');

  constructor() {
    addIcons({ list, map, notifications, search });
    // Service worker e, con un avviso acceso, l'iscrizione rinfrescata (con
    // la posizione nuova, se il permesso c'è già). In sottofondo: l'app non
    // aspetta, e se non riesce ci riprova alla prossima apertura.
    void this.notifiche.avvio();
    this.router.events.subscribe((evento) => {
      if (evento instanceof NavigationEnd) {
        const scheda = schedaDi(evento.urlAfterRedirects);
        if (scheda) {
          this.scheda.set(scheda);
        }
      }
    });
  }

  vai(indirizzo: string): void {
    void this.navigazione.navigateRoot(indirizzo, { animated: false });
  }
}
