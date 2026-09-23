import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInput,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { MenuUtenteComponent } from '../../componenti/menu-utente/menu-utente.component';

/**
 * Prima schermata della Funzione 1 (Ricerca campo): titolo, casella di
 * ricerca e pulsante "Vai", come nei mockup grafica/home.jpg e
 * grafica/FunzioneUnoSchermataUno.jpg.
 */
@Component({
  selector: 'pagina-cerca',
  imports: [
    FormsModule,
    RouterLink,
    MenuUtenteComponent,
    IonButton,
    IonButtons,
    IonContent,
    IonHeader,
    IonInput,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './cerca.page.html',
  styleUrl: './cerca.page.scss',
})
export class CercaPage {
  private readonly router = inject(Router);

  readonly testo = signal('');
  readonly pronto = computed(() => this.testo().trim().length > 0);
  cerca(): void {
    const termine = this.testo().trim();

    if (termine.length === 0) {
      return;
    }

    this.router.navigate(['/risultati'], { queryParams: { q: termine } });
  }
}
