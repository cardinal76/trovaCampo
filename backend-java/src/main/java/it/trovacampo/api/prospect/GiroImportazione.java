package it.trovacampo.api.prospect;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * L'ultimo giro di importazione, in Mongo perché sopravviva a un riavvio:
 * con che opzioni riprenderlo, in che fase è, se il registro l'ha bloccato
 * e quante richieste si sono fatte oggi (per il tetto giornaliero). Un solo
 * documento, {@link #ID}.
 */
@Document(collection = "prospect_importazione_giro")
public class GiroImportazione {

    static final String ID = "rasd";

    @Id private String id = ID;

    private List<Integer> regioni;
    private AmbitoDettagli dettagli;
    private String avviatoDa;
    private Instant iniziato;
    /** «in corso», «in pausa», «bloccata», «fermata», «completata», «errore». */
    private String fase;
    private String motivo;
    private Instant bloccataIl;
    private Instant aggiornato;
    /** Il giorno (ora italiana, AAAA-MM-GG) a cui si riferisce {@link #richiesteGiorno}. */
    private String giorno;
    private int richiesteGiorno;

    public String getId() {
        return id;
    }

    public GiroImportazione setId(String id) {
        this.id = id;
        return this;
    }

    public List<Integer> getRegioni() {
        return regioni;
    }

    public GiroImportazione setRegioni(List<Integer> regioni) {
        this.regioni = regioni;
        return this;
    }

    public AmbitoDettagli getDettagli() {
        return dettagli;
    }

    public GiroImportazione setDettagli(AmbitoDettagli dettagli) {
        this.dettagli = dettagli;
        return this;
    }

    public String getAvviatoDa() {
        return avviatoDa;
    }

    public GiroImportazione setAvviatoDa(String avviatoDa) {
        this.avviatoDa = avviatoDa;
        return this;
    }

    public Instant getIniziato() {
        return iniziato;
    }

    public GiroImportazione setIniziato(Instant iniziato) {
        this.iniziato = iniziato;
        return this;
    }

    public String getFase() {
        return fase;
    }

    public GiroImportazione setFase(String fase) {
        this.fase = fase;
        return this;
    }

    public String getMotivo() {
        return motivo;
    }

    public GiroImportazione setMotivo(String motivo) {
        this.motivo = motivo;
        return this;
    }

    public Instant getBloccataIl() {
        return bloccataIl;
    }

    public GiroImportazione setBloccataIl(Instant bloccataIl) {
        this.bloccataIl = bloccataIl;
        return this;
    }

    public Instant getAggiornato() {
        return aggiornato;
    }

    public GiroImportazione setAggiornato(Instant aggiornato) {
        this.aggiornato = aggiornato;
        return this;
    }

    public String getGiorno() {
        return giorno;
    }

    public GiroImportazione setGiorno(String giorno) {
        this.giorno = giorno;
        return this;
    }

    public int getRichiesteGiorno() {
        return richiesteGiorno;
    }

    public GiroImportazione setRichiesteGiorno(int richiesteGiorno) {
        this.richiesteGiorno = richiesteGiorno;
        return this;
    }
}
