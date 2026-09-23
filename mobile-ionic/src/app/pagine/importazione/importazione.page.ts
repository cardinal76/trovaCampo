import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonChip,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonNote,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { checkmarkCircle, cloudUpload, documentAttach, warning } from 'ionicons/icons';
import { EsitoImportazione } from '../../modelli/importazione';
import { ImportazioneService } from '../../servizi/importazione.service';

/**
 * Importazione da Excel per chi amministra. Non è collegata dal resto
 * dell'app: ci si arriva da /admin/importazione e serve il token del server.
 *
 * Il salvataggio vero è possibile solo dopo una prova andata a buon fine sullo
 * stesso file, così prima di scrivere nell'archivio si vede sempre cosa
 * succederà.
 */
@Component({
  selector: 'pagina-importazione',
  imports: [
    FormsModule,
    IonBackButton,
    IonButton,
    IonButtons,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonChip,
    IonContent,
    IonHeader,
    IonIcon,
    IonInput,
    IonItem,
    IonLabel,
    IonList,
    IonNote,
    IonSpinner,
    IonText,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './importazione.page.html',
  styleUrl: './importazione.page.scss',
})
export class ImportazionePage {
  private readonly service = inject(ImportazioneService);

  readonly token = signal(leggiTokenSalvato());
  readonly file = signal<File | null>(null);
  readonly inCorso = signal(false);
  readonly errore = signal<string | null>(null);
  /** Esito dell'ultima prova sul file scelto: sblocca il salvataggio. */
  readonly prova = signal<EsitoImportazione | null>(null);
  /** Esito del salvataggio vero. */
  readonly salvato = signal<EsitoImportazione | null>(null);

  readonly esito = computed(() => this.salvato() ?? this.prova());
  readonly pronto = computed(() => this.token().trim().length > 0 && this.file() !== null);
  readonly daScrivere = computed(() => {
    const esito = this.prova();
    return esito ? esito.inserite + esito.aggiornate : 0;
  });
  /** Mille righe sono circa venti minuti: una geocodifica ogni 1,1 secondi. */
  readonly minutiGeocodifica = computed(() =>
    Math.max(1, Math.ceil(((this.esito()?.daGeocodificare ?? 0) * 1.1) / 60)),
  );

  constructor() {
    addIcons({ checkmarkCircle, cloudUpload, documentAttach, warning });
  }

  cambiaToken(valore: string | null | undefined): void {
    this.token.set(valore ?? '');
    this.azzera();
  }

  scegliFile(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    this.azzera();
    // Svuotato così scegliere di nuovo lo stesso file (magari corretto nel
    // frattempo) fa scattare comunque l'evento.
    input.value = '';
  }

  controlla(): void {
    this.invia(true);
  }

  importa(): void {
    if (this.prova()) {
      this.invia(false);
    }
  }

  private invia(prova: boolean): void {
    const file = this.file();
    if (!this.pronto() || !file || this.inCorso()) {
      return;
    }

    this.inCorso.set(true);
    this.errore.set(null);

    this.service.importa(file, this.token(), prova).subscribe({
      next: (esito) => {
        this.inCorso.set(false);
        salvaToken(this.token());
        if (prova) {
          this.prova.set(esito);
        } else {
          this.salvato.set(esito);
        }
      },
      error: (errore: Error) => {
        this.inCorso.set(false);
        this.errore.set(errore.message);
      },
    });
  }

  private azzera(): void {
    this.prova.set(null);
    this.salvato.set(null);
    this.errore.set(null);
  }
}

/*
 * Il token resta solo per la sessione del browser: comodo per più file di
 * fila, senza lasciarlo su un dispositivo condiviso. sessionStorage può non
 * esserci (navigazione privata, cookie bloccati): allora si riscrive.
 */
const CHIAVE_TOKEN = 'trovacampo.tokenImportazione';

function leggiTokenSalvato(): string {
  try {
    return sessionStorage.getItem(CHIAVE_TOKEN) ?? '';
  } catch {
    return '';
  }
}

function salvaToken(token: string): void {
  try {
    sessionStorage.setItem(CHIAVE_TOKEN, token.trim());
  } catch {
    // Pazienza: al prossimo caricamento andrà reinserito.
  }
}
