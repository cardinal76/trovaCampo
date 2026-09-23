import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonListHeader,
  IonNote,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { create } from 'ionicons/icons';
import { Societa, indirizzoCompleto, nomeCompleto } from '../../modelli/societa';
import { amministratoreRicordato } from '../../servizi/amministratore-ricordato';
import { SocietaService } from '../../servizi/societa.service';

type Stato = 'caricamento' | 'completata' | 'errore';

/**
 * Funzioni 2 e 3 riunite in un'unica schermata, come nel mockup
 * grafica/anagraficaSocietà.jpg: anagrafica della società e campionati a cui
 * partecipa, distinti fra agonistica e scuola calcio.
 */
@Component({
  selector: 'pagina-scheda',
  imports: [
    RouterLink,
    IonBackButton,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonIcon,
    IonItem,
    IonLabel,
    IonList,
    IonListHeader,
    IonNote,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './scheda.page.html',
})
export class SchedaPage {
  private readonly rotta = inject(ActivatedRoute);
  private readonly service = inject(SocietaService);

  readonly id = this.rotta.snapshot.paramMap.get('id') ?? '';
  readonly societa = signal<Societa | null>(null);
  readonly stato = signal<Stato>('caricamento');
  /** Il pulsante "Modifica": solo se su questo browser è entrato un amministratore. */
  readonly amministratore = signal(amministratoreRicordato());

  readonly indirizzoCampo = computed(() => {
    const societa = this.societa();
    return societa ? indirizzoCompleto(societa) : '';
  });

  readonly titolo = computed(() => {
    const societa = this.societa();
    return societa ? nomeCompleto(societa) : 'Scheda società';
  });

  /** Un campo inserito a mano con la sola Funzione 1 non ha anagrafica. */
  readonly haAnagrafica = computed(() => {
    const societa = this.societa();
    return Boolean(
      societa &&
        (societa.presidente ||
          societa.indirizzoSede ||
          societa.telefono ||
          societa.email ||
          societa.sitoWeb ||
          societa.matricola),
    );
  });

  readonly campionati = computed(() => this.societa()?.campionati ?? []);
  readonly agonistica = computed(() => this.campionati().filter((c) => c.tipo === 'Agonistica'));
  readonly scuolaCalcio = computed(() =>
    this.campionati().filter((c) => c.tipo === 'ScuolaCalcio'),
  );

  readonly prezziScuolaCalcio = computed(() => {
    const societa = this.societa();

    if (!societa || societa.scuolaCalcio === undefined) {
      return null;
    }

    if (!societa.scuolaCalcio) {
      return 'No';
    }

    return societa.prezziScuolaCalcio ? `Sì — ${societa.prezziScuolaCalcio}` : 'Sì';
  });

  constructor() {
    addIcons({ create });
  }

  /**
   * A ogni ingresso e non solo alla creazione: tornando dalla modifica la
   * scheda deve mostrare i dati appena salvati.
   */
  ionViewWillEnter(): void {
    this.amministratore.set(amministratoreRicordato());
    this.service.perId(this.id).subscribe({
      next: (dati) => {
        this.societa.set(dati);
        this.stato.set('completata');
      },
      error: () => this.stato.set('errore'),
    });
  }
}
