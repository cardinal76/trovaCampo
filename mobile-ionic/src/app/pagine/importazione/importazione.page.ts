import { Component, computed, inject, signal } from '@angular/core';
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
import { checkmarkCircle, cloudUpload, documentAttach, logOut, warning } from 'ionicons/icons';
import { EsitoImportazione } from '../../modelli/importazione';
import {
  AutenticazioneService,
  RUOLO_AMMINISTRATORE,
} from '../../servizi/autenticazione.service';
import { ImportazioneService } from '../../servizi/importazione.service';

/**
 * Importazione da Excel per chi amministra. Non è collegata dal resto
 * dell'app: ci si arriva da /admin/importazione, e aprirla porta al login del
 * Keycloak di presenze. Serve il ruolo trovacampo-admin.
 *
 * Il salvataggio vero è possibile solo dopo una prova andata a buon fine sullo
 * stesso file, così prima di scrivere nell'archivio si vede sempre cosa
 * succederà.
 */
@Component({
  selector: 'pagina-importazione',
  imports: [
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
  readonly autenticazione = inject(AutenticazioneService);
  readonly ruolo = RUOLO_AMMINISTRATORE;

  /** Il login su Keycloak: finché non è 'entrato' il form non si vede. */
  readonly accesso = signal<'in-corso' | 'entrato' | 'errore'>('in-corso');
  readonly file = signal<File | null>(null);
  readonly inCorso = signal(false);
  readonly errore = signal<string | null>(null);
  /** Esito dell'ultima prova sul file scelto: sblocca il salvataggio. */
  readonly prova = signal<EsitoImportazione | null>(null);
  /** Esito del salvataggio vero. */
  readonly salvato = signal<EsitoImportazione | null>(null);

  readonly esito = computed(() => this.salvato() ?? this.prova());
  readonly pronto = computed(
    () => this.autenticazione.amministratore() && this.file() !== null,
  );
  readonly daScrivere = computed(() => {
    const esito = this.prova();
    return esito ? esito.inserite + esito.aggiornate : 0;
  });
  /** Mille righe sono circa venti minuti: una geocodifica ogni 1,1 secondi. */
  readonly minutiGeocodifica = computed(() =>
    Math.max(1, Math.ceil(((this.esito()?.daGeocodificare ?? 0) * 1.1) / 60)),
  );

  constructor() {
    addIcons({ checkmarkCircle, cloudUpload, documentAttach, logOut, warning });
    this.entra();
  }

  entra(): void {
    this.accesso.set('in-corso');
    this.autenticazione.accedi().then(
      (entrato) => this.accesso.set(entrato ? 'entrato' : 'errore'),
      () => this.accesso.set('errore'),
    );
  }

  esci(): void {
    void this.autenticazione.esci();
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

    this.service.importa(file, prova).subscribe({
      next: (esito) => {
        this.inCorso.set(false);
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
