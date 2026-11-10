import { Component, computed, inject, signal } from '@angular/core';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInput,
  IonItem,
  IonList,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonText,
  IonTextarea,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular/standalone';
import { SegnalazioniService, TipoRettifica } from '../../servizi/segnalazioni/segnalazioni.service';

/**
 * «Hai dati da rettificare?»: chi vede un nome di società o un indirizzo di
 * campo sbagliato lo scrive qui. Pubblica, senza login. Dalla scheda arriva
 * con `?societa=<id>&nome=<nome>`, e la società è già indicata.
 */
@Component({
  selector: 'pagina-rettifica',
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
    IonSelect,
    IonSelectOption,
    IonSpinner,
    IonText,
    IonTextarea,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './rettifica.page.html',
})
export class RettificaPage {
  private readonly service = inject(SegnalazioniService);
  private readonly toast = inject(ToastController);
  private readonly posizione = inject(Location);
  private readonly parametri = inject(ActivatedRoute).snapshot.queryParamMap;

  readonly societaId = this.parametri.get('societa') ?? undefined;
  readonly nomeSocieta = this.parametri.get('nome') ?? '';

  readonly tipo = signal<TipoRettifica>('NOME_SOCIETA');
  readonly attuale = signal(this.nomeSocieta);
  readonly corretto = signal('');
  readonly contatto = signal('');
  readonly sito = signal('');
  readonly invio = signal(false);

  readonly pronto = computed(() => this.corretto().trim().length > 0);

  invia(): void {
    if (!this.pronto() || this.invio()) {
      return;
    }
    this.invio.set(true);
    this.service
      .invia({
        tipo: this.tipo(),
        societaId: this.societaId,
        attuale: this.attuale().trim() || undefined,
        corretto: this.corretto().trim(),
        contatto: this.contatto().trim() || undefined,
        sito: this.sito() || undefined,
      })
      .subscribe({
        next: async () => {
          this.invio.set(false);
          await this.avvisa('Grazie! Controlliamo e correggiamo appena possibile.', 'success');
          this.posizione.back();
        },
        error: async (errore) => {
          this.invio.set(false);
          await this.avvisa(
            errore?.status === 429
              ? 'Troppe segnalazioni in questo momento. Riprova più tardi.'
              : 'Non è stato possibile inviare la segnalazione. Controlla i dati e riprova.',
            'danger',
          );
        },
      });
  }

  private async avvisa(messaggio: string, colore: 'success' | 'danger'): Promise<void> {
    const avviso = await this.toast.create({ message: messaggio, duration: 3000, color: colore });
    await avviso.present();
  }
}
