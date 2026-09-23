import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IonButton, IonContent, IonIcon, IonInput } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { cloudUpload } from 'ionicons/icons';
import { amministratoreRicordato } from '../../servizi/amministratore-ricordato';

/**
 * Prima schermata della Funzione 1 (Ricerca campo): titolo, casella di
 * ricerca e pulsante "Vai", come nei mockup grafica/home.jpg e
 * grafica/FunzioneUnoSchermataUno.jpg.
 */
@Component({
  selector: 'pagina-cerca',
  imports: [FormsModule, RouterLink, IonButton, IonContent, IonIcon, IonInput],
  templateUrl: './cerca.page.html',
  styleUrl: './cerca.page.scss',
})
export class CercaPage {
  private readonly router = inject(Router);

  readonly testo = signal('');
  readonly pronto = computed(() => this.testo().trim().length > 0);
  /** Pulsante dell'importazione: solo se qui è entrato un amministratore. */
  readonly amministratore = signal(amministratoreRicordato());

  constructor() {
    addIcons({ cloudUpload });
  }

  /**
   * Ionic tiene in vita la home mentre si naviga: tornando dalla pagina di
   * importazione, dopo un login o un'uscita, il pulsante va riletto.
   */
  ionViewWillEnter(): void {
    this.amministratore.set(amministratoreRicordato());
  }

  cerca(): void {
    const termine = this.testo().trim();

    if (termine.length === 0) {
      return;
    }

    this.router.navigate(['/risultati'], { queryParams: { q: termine } });
  }
}
