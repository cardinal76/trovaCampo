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
import {
  arrowUndo,
  checkmarkCircle,
  cloudUpload,
  documentAttach,
  logOut,
  sync,
  warning,
} from 'ionicons/icons';
import { EsitoImportazione } from '../../modelli/importazione';
import { Esclusione } from '../../modelli/societa';
import { AmministrazioneService } from '../../servizi/amministrazione.service';
import {
  AutenticazioneService,
  RUOLO_AMMINISTRATORE,
} from '../../servizi/autenticazione.service';
import { ImportazioneService } from '../../servizi/importazione.service';

/**
 * Importazione di campi, da Excel o dal PDF di un comunicato, per chi
 * amministra. Ci si arriva dal pulsante in home (solo per l'amministratore) o
 * da /admin/importazione, e aprirla porta al login del Keycloak di presenze.
 * Serve il ruolo trovacampo-admin.
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
  private readonly amministrazione = inject(AmministrazioneService);
  readonly autenticazione = inject(AutenticazioneService);
  readonly ruolo = RUOLO_AMMINISTRATORE;

  /** Il login su Keycloak: finché non è 'entrato' il form non si vede. */
  readonly accesso = signal<'in-corso' | 'entrato' | 'errore'>('in-corso');
  readonly file = signal<File | null>(null);
  /** Da dove vengono le righe dell'ultimo controllo: il file scelto o l'anagrafica di presenze. */
  readonly sorgente = signal<'file' | 'anagrafica'>('file');
  readonly inCorso = signal(false);
  readonly errore = signal<string | null>(null);
  /** Esito dell'ultima prova sul file scelto: sblocca il salvataggio. */
  readonly prova = signal<EsitoImportazione | null>(null);
  /** Esito del salvataggio vero. */
  readonly salvato = signal<EsitoImportazione | null>(null);

  /**
   * I campi di presenze eliminati da chi amministra, che la sincronizzazione
   * salta. Null finché non arrivano; se non arrivano la sezione non compare.
   */
  readonly esclusioni = signal<Esclusione[] | null>(null);
  readonly erroreEsclusioni = signal<string | null>(null);
  /** L'id dell'esclusione che si sta annullando. */
  readonly annullamento = signal<string | null>(null);

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
    addIcons({ arrowUndo, checkmarkCircle, cloudUpload, documentAttach, logOut, sync, warning });
    this.entra();
  }

  entra(): void {
    this.accesso.set('in-corso');
    this.autenticazione.accedi().then(
      (entrato) => {
        this.accesso.set(entrato ? 'entrato' : 'errore');
        if (entrato && this.autenticazione.amministratore()) {
          this.caricaEsclusioni();
        }
      },
      () => this.accesso.set('errore'),
    );
  }

  esci(): void {
    void this.autenticazione.esci();
  }

  caricaEsclusioni(): void {
    this.amministrazione.esclusioni().subscribe({
      next: (esclusioni) => {
        this.esclusioni.set(esclusioni);
        this.erroreEsclusioni.set(null);
      },
      error: (errore: Error) => this.erroreEsclusioni.set(errore.message),
    });
  }

  /**
   * Riammette un campo eliminato per sbaglio: torna alla prossima
   * sincronizzazione (o subito, con "Controlla l'anagrafica di presenze"),
   * se presenze lo manda ancora.
   */
  annullaEsclusione(esclusione: Esclusione): void {
    if (this.annullamento()) {
      return;
    }
    this.annullamento.set(esclusione.id);
    this.amministrazione.annullaEsclusione(esclusione.id).subscribe({
      next: () => {
        this.annullamento.set(null);
        this.esclusioni.update((elenco) => (elenco ?? []).filter((e) => e.id !== esclusione.id));
      },
      error: (errore: Error) => {
        this.annullamento.set(null);
        this.erroreEsclusioni.set(errore.message);
      },
    });
  }

  quando(esclusione: Esclusione): string {
    return esclusione.esclusaIl
      ? new Date(esclusione.esclusaIl).toLocaleDateString('it-IT', {
          day: 'numeric',
          month: 'short',
          year: 'numeric',
        })
      : '';
  }

  scegliFile(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    this.sorgente.set('file');
    this.azzera();
    // Svuotato così scegliere di nuovo lo stesso file (magari corretto nel
    // frattempo) fa scattare comunque l'evento.
    input.value = '';
  }

  controlla(): void {
    this.invia(true);
  }

  /**
   * I campi letti da presenze nei Comunicati Ufficiali: si controllano come
   * un file, e il file eventualmente scelto non conta più.
   */
  controllaAnagrafica(): void {
    this.file.set(null);
    this.sorgente.set('anagrafica');
    this.azzera();
    this.invia(true);
  }

  importa(): void {
    if (this.prova()) {
      this.invia(false);
    }
  }

  private invia(prova: boolean): void {
    const file = this.file();
    const dallAnagrafica = this.sorgente() === 'anagrafica';
    if (!this.autenticazione.amministratore() || this.inCorso() || (!dallAnagrafica && !file)) {
      return;
    }

    this.inCorso.set(true);
    this.errore.set(null);

    const richiesta = dallAnagrafica
      ? this.service.sincronizza(prova)
      : this.service.importa(file!, prova);
    richiesta.subscribe({
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
