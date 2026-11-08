package it.trovacampo.api.prospect;

import it.trovacampo.api.indirizzi.Confidenza;
import it.trovacampo.api.indirizzi.FonteIndirizzo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una ASD/SSD del Registro nazionale delle attività sportive dilettantistiche
 * (RASD, Sport e Salute), con la chiave del suo codice fiscale.
 *
 * <p><b>Solo dati della persona giuridica.</b> Il dettaglio del registro
 * porta anche il nome del legale rappresentante: qui non c'è un campo dove
 * metterlo, e {@link RegistroRasd} non lo legge nemmeno (prende le voci del
 * dettaglio per nome, da un elenco chiuso). Chi aggiunge un campo a questa
 * classe controlli che non sia un dato di una persona fisica.
 *
 * <p>È una lista commerciale: si vede solo dall'amministrazione
 * ({@code /api/admin/prospect}), mai dalle API pubbliche.
 */
@Document(collection = "prospect")
public class Prospect {

    /** Il codice fiscale della società: la chiave, stabile fra un'importazione e l'altra. */
    @Id private String codiceFiscale;

    /** L'id della domanda nel registro: serve solo a chiederne il dettaglio. */
    private Long rasdId;

    private String denominazione;
    private String naturaGiuridica;
    private String regione;
    /** Il comune della sede legale: l'unico indirizzo che il registro dà. */
    private String comune;
    /** Le sigle degli organismi con un'affiliazione attiva (FIGC, CSI, UISP…). */
    private List<String> organismi;
    private LocalDate dataIscrizione;

    /** Vero con un'affiliazione FIGC attiva, o con una disciplina «Calcio…» nel dettaglio. */
    private boolean calcio;

    // Dal dettaglio: per ora solo Lazio e calcio, vedi ImportazioneProspect.
    private List<String> discipline;
    private Integer tesseratiAttivi;
    private Integer attivitaSportiveUltimoAnno;
    private Integer partecipazioniUltimoAnno;
    private Instant dettaglioLetto;

    // Il campo di gioco, quando si trova.
    private String nomeImpianto;
    private String indirizzo;
    private String comuneCampo;
    private Double lat;
    private Double lng;
    /** Da dove viene l'indirizzo: vedi {@link FonteIndirizzo}. Vuoto finché non c'è. */
    private FonteIndirizzo fonteIndirizzo;
    /** Come si è trovato il collegamento (per nome esatto, per parole, con il comune). */
    private String collegamento;
    /** Quanto è sicuro l'indirizzo: vedi {@link Confidenza}. */
    private Confidenza confidenzaIndirizzo;
    /**
     * Quando l'indirizzo si è cercato anche su OpenStreetMap: la ricerca con
     * la rete non si ripete, salvo chiederlo. Vuoto se non si è mai cercato.
     */
    private Instant geocodificaTentata;
    /** La scheda di TrovaCampo da cui viene il campo, se viene da lì. */
    private String societaTrovaCampoId;
    /** L'impianto dell'anagrafica di presenze, se il campo viene da lì. */
    private Long anagraficaImpiantoId;

    private Instant aggiornato;

    public String getCodiceFiscale() {
        return codiceFiscale;
    }

    public Prospect setCodiceFiscale(String codiceFiscale) {
        this.codiceFiscale = codiceFiscale;
        return this;
    }

    public Long getRasdId() {
        return rasdId;
    }

    public Prospect setRasdId(Long rasdId) {
        this.rasdId = rasdId;
        return this;
    }

    public String getDenominazione() {
        return denominazione;
    }

    public Prospect setDenominazione(String denominazione) {
        this.denominazione = denominazione;
        return this;
    }

    public String getNaturaGiuridica() {
        return naturaGiuridica;
    }

    public Prospect setNaturaGiuridica(String naturaGiuridica) {
        this.naturaGiuridica = naturaGiuridica;
        return this;
    }

    public String getRegione() {
        return regione;
    }

    public Prospect setRegione(String regione) {
        this.regione = regione;
        return this;
    }

    public String getComune() {
        return comune;
    }

    public Prospect setComune(String comune) {
        this.comune = comune;
        return this;
    }

    public List<String> getOrganismi() {
        return organismi;
    }

    public Prospect setOrganismi(List<String> organismi) {
        this.organismi = organismi;
        return this;
    }

    public LocalDate getDataIscrizione() {
        return dataIscrizione;
    }

    public Prospect setDataIscrizione(LocalDate dataIscrizione) {
        this.dataIscrizione = dataIscrizione;
        return this;
    }

    public boolean isCalcio() {
        return calcio;
    }

    public Prospect setCalcio(boolean calcio) {
        this.calcio = calcio;
        return this;
    }

    public List<String> getDiscipline() {
        return discipline;
    }

    public Prospect setDiscipline(List<String> discipline) {
        this.discipline = discipline;
        return this;
    }

    public Integer getTesseratiAttivi() {
        return tesseratiAttivi;
    }

    public Prospect setTesseratiAttivi(Integer tesseratiAttivi) {
        this.tesseratiAttivi = tesseratiAttivi;
        return this;
    }

    public Integer getAttivitaSportiveUltimoAnno() {
        return attivitaSportiveUltimoAnno;
    }

    public Prospect setAttivitaSportiveUltimoAnno(Integer attivitaSportiveUltimoAnno) {
        this.attivitaSportiveUltimoAnno = attivitaSportiveUltimoAnno;
        return this;
    }

    public Integer getPartecipazioniUltimoAnno() {
        return partecipazioniUltimoAnno;
    }

    public Prospect setPartecipazioniUltimoAnno(Integer partecipazioniUltimoAnno) {
        this.partecipazioniUltimoAnno = partecipazioniUltimoAnno;
        return this;
    }

    public Instant getDettaglioLetto() {
        return dettaglioLetto;
    }

    public Prospect setDettaglioLetto(Instant dettaglioLetto) {
        this.dettaglioLetto = dettaglioLetto;
        return this;
    }

    public String getNomeImpianto() {
        return nomeImpianto;
    }

    public Prospect setNomeImpianto(String nomeImpianto) {
        this.nomeImpianto = nomeImpianto;
        return this;
    }

    public String getIndirizzo() {
        return indirizzo;
    }

    public Prospect setIndirizzo(String indirizzo) {
        this.indirizzo = indirizzo;
        return this;
    }

    public String getComuneCampo() {
        return comuneCampo;
    }

    public Prospect setComuneCampo(String comuneCampo) {
        this.comuneCampo = comuneCampo;
        return this;
    }

    public Double getLat() {
        return lat;
    }

    public Prospect setLat(Double lat) {
        this.lat = lat;
        return this;
    }

    public Double getLng() {
        return lng;
    }

    public Prospect setLng(Double lng) {
        this.lng = lng;
        return this;
    }

    public FonteIndirizzo getFonteIndirizzo() {
        return fonteIndirizzo;
    }

    public Prospect setFonteIndirizzo(FonteIndirizzo fonteIndirizzo) {
        this.fonteIndirizzo = fonteIndirizzo;
        return this;
    }

    public String getCollegamento() {
        return collegamento;
    }

    public Prospect setCollegamento(String collegamento) {
        this.collegamento = collegamento;
        return this;
    }

    public Confidenza getConfidenzaIndirizzo() {
        return confidenzaIndirizzo;
    }

    public Prospect setConfidenzaIndirizzo(Confidenza confidenzaIndirizzo) {
        this.confidenzaIndirizzo = confidenzaIndirizzo;
        return this;
    }

    public Instant getGeocodificaTentata() {
        return geocodificaTentata;
    }

    public Prospect setGeocodificaTentata(Instant geocodificaTentata) {
        this.geocodificaTentata = geocodificaTentata;
        return this;
    }

    public String getSocietaTrovaCampoId() {
        return societaTrovaCampoId;
    }

    public Prospect setSocietaTrovaCampoId(String societaTrovaCampoId) {
        this.societaTrovaCampoId = societaTrovaCampoId;
        return this;
    }

    public Long getAnagraficaImpiantoId() {
        return anagraficaImpiantoId;
    }

    public Prospect setAnagraficaImpiantoId(Long anagraficaImpiantoId) {
        this.anagraficaImpiantoId = anagraficaImpiantoId;
        return this;
    }

    public Instant getAggiornato() {
        return aggiornato;
    }

    public Prospect setAggiornato(Instant aggiornato) {
        this.aggiornato = aggiornato;
        return this;
    }
}
