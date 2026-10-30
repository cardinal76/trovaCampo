package it.trovacampo.api.prospect;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A che punto è l'importazione di una regione: il prossimo {@code start}
 * da chiedere al registro. Si salva dopo ogni pagina, così un'importazione
 * fermata (a mano, da un errore o da un riavvio) riparte da lì.
 */
@Document(collection = "prospect_importazione")
public class StatoImportazioneProspect {

    /** L'id della regione nel registro (Lazio = 12). */
    @Id private Integer regioneId;

    private String regione;
    private int prossimoStart;
    /** Quante società il registro dice di avere per la regione, all'ultima pagina letta. */
    private Integer totale;
    private boolean completata;
    private Instant aggiornato;

    public StatoImportazioneProspect() {}

    public StatoImportazioneProspect(Integer regioneId, String regione) {
        this.regioneId = regioneId;
        this.regione = regione;
    }

    public Integer getRegioneId() {
        return regioneId;
    }

    public StatoImportazioneProspect setRegioneId(Integer regioneId) {
        this.regioneId = regioneId;
        return this;
    }

    public String getRegione() {
        return regione;
    }

    public StatoImportazioneProspect setRegione(String regione) {
        this.regione = regione;
        return this;
    }

    public int getProssimoStart() {
        return prossimoStart;
    }

    public StatoImportazioneProspect setProssimoStart(int prossimoStart) {
        this.prossimoStart = prossimoStart;
        return this;
    }

    public Integer getTotale() {
        return totale;
    }

    public StatoImportazioneProspect setTotale(Integer totale) {
        this.totale = totale;
        return this;
    }

    public boolean isCompletata() {
        return completata;
    }

    public StatoImportazioneProspect setCompletata(boolean completata) {
        this.completata = completata;
        return this;
    }

    public Instant getAggiornato() {
        return aggiornato;
    }

    public StatoImportazioneProspect setAggiornato(Instant aggiornato) {
        this.aggiornato = aggiornato;
        return this;
    }
}
