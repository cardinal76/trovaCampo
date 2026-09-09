import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Location } from '@angular/common';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInput,
  IonItem,
  IonList,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular/standalone';
import { SocietaService } from '../../servizi/societa.service';

/**
 * Segnalazione di un campo mancante. I tre campi sono normali caselle di
 * testo: si può digitare oppure dettare con il microfono già presente sulla
 * tastiera di iOS e Android, senza librerie aggiuntive.
 */
@Component({
  selector: 'pagina-aggiungi',
  imports: [
    FormsModule,
    IonBackButton,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonInput,
    IonItem,
    IonList,
    IonSpinner,
    IonText,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './aggiungi.page.html',
})
export class AggiungiPage {
  private readonly service = inject(SocietaService);
  private readonly toast = inject(ToastController);
  private readonly posizione = inject(Location);

  readonly nomeCampo = signal('');
  readonly nomeSocieta = signal('');
  readonly indirizzo = signal('');
  readonly salvataggio = signal(false);

  readonly pronto = computed(
    () =>
      this.nomeCampo().trim().length > 0 &&
      this.nomeSocieta().trim().length > 0 &&
      this.indirizzo().trim().length > 0,
  );

  salva(): void {
    if (!this.pronto() || this.salvataggio()) {
      return;
    }

    this.salvataggio.set(true);

    this.service
      .inserisci({
        nomeImpianto: this.nomeCampo().trim(),
        nomeSocieta: this.nomeSocieta().trim(),
        indirizzoImpianto: this.indirizzo().trim(),
      })
      .subscribe({
        next: async () => {
          this.salvataggio.set(false);
          await this.avvisa('Campo aggiunto. Grazie per il contributo!', 'success');
          this.posizione.back();
        },
        error: async () => {
          this.salvataggio.set(false);
          await this.avvisa('Non è stato possibile salvare il campo. Riprova.', 'danger');
        },
      });
  }

  private async avvisa(messaggio: string, colore: 'success' | 'danger'): Promise<void> {
    const avviso = await this.toast.create({ message: messaggio, duration: 2500, color: colore });
    await avviso.present();
  }
}
