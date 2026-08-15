package it.trovacampo.api.dominio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Società dilettantistica con il suo campo di gioco.
 *
 * <p>I nomi dei campi coincidono con quelli dell'API Node esistente
 * ({@code backend/src/types.ts}), così i client possono puntare
 * indifferentemente all'una o all'altra implementazione.
 *
 * <p>Anagrafica (Funzione 2) e campionati (Funzione 3) sono facoltativi:
 * un campo inserito a mano con la sola Funzione 1 ne è privo.
 */
@Document(collection = "societa")
public class Societa {

    @Id
    private String id;

    private String siglaSocieta;
    private String nomeSocieta;
    private String comitatoRegionale;
    private String nomeImpianto;
    private String indirizzoImpianto;
    private String localitaImpianto;
    private String provinciaImpianto;
    private Double lat;
    private Double lng;

    private String matricola;
    private String presidente;
    private String indirizzoSede;
    private String telefono;
    private String fax;
    private String email;
    private String sitoWeb;
    private Boolean scuolaCalcio;
    private String prezziScuolaCalcio;

    private List<Campionato> campionati;

    /**
     * Copia normalizzata (minuscolo, senza accenti) dei campi ricercabili:
     * permette una ricerca insensibile ad accenti e maiuscole con una sola
     * espressione regolare. Non fa parte della risposta dell'API.
     */
    @JsonIgnore
    private String testoRicerca;

    public String getId() {
        return id;
    }

    public Societa setId(String id) {
        this.id = id;
        return this;
    }

    public String getSiglaSocieta() {
        return siglaSocieta;
    }

    public Societa setSiglaSocieta(String siglaSocieta) {
        this.siglaSocieta = siglaSocieta;
        return this;
    }

    public String getNomeSocieta() {
        return nomeSocieta;
    }

    public Societa setNomeSocieta(String nomeSocieta) {
        this.nomeSocieta = nomeSocieta;
        return this;
    }

    public String getComitatoRegionale() {
        return comitatoRegionale;
    }

    public Societa setComitatoRegionale(String comitatoRegionale) {
        this.comitatoRegionale = comitatoRegionale;
        return this;
    }

    public String getNomeImpianto() {
        return nomeImpianto;
    }

    public Societa setNomeImpianto(String nomeImpianto) {
        this.nomeImpianto = nomeImpianto;
        return this;
    }

    public String getIndirizzoImpianto() {
        return indirizzoImpianto;
    }

    public Societa setIndirizzoImpianto(String indirizzoImpianto) {
        this.indirizzoImpianto = indirizzoImpianto;
        return this;
    }

    public String getLocalitaImpianto() {
        return localitaImpianto;
    }

    public Societa setLocalitaImpianto(String localitaImpianto) {
        this.localitaImpianto = localitaImpianto;
        return this;
    }

    public String getProvinciaImpianto() {
        return provinciaImpianto;
    }

    public Societa setProvinciaImpianto(String provinciaImpianto) {
        this.provinciaImpianto = provinciaImpianto;
        return this;
    }

    public Double getLat() {
        return lat;
    }

    public Societa setLat(Double lat) {
        this.lat = lat;
        return this;
    }

    public Double getLng() {
        return lng;
    }

    public Societa setLng(Double lng) {
        this.lng = lng;
        return this;
    }

    public String getMatricola() {
        return matricola;
    }

    public Societa setMatricola(String matricola) {
        this.matricola = matricola;
        return this;
    }

    public String getPresidente() {
        return presidente;
    }

    public Societa setPresidente(String presidente) {
        this.presidente = presidente;
        return this;
    }

    public String getIndirizzoSede() {
        return indirizzoSede;
    }

    public Societa setIndirizzoSede(String indirizzoSede) {
        this.indirizzoSede = indirizzoSede;
        return this;
    }

    public String getTelefono() {
        return telefono;
    }

    public Societa setTelefono(String telefono) {
        this.telefono = telefono;
        return this;
    }

    public String getFax() {
        return fax;
    }

    public Societa setFax(String fax) {
        this.fax = fax;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public Societa setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getSitoWeb() {
        return sitoWeb;
    }

    public Societa setSitoWeb(String sitoWeb) {
        this.sitoWeb = sitoWeb;
        return this;
    }

    public Boolean getScuolaCalcio() {
        return scuolaCalcio;
    }

    public Societa setScuolaCalcio(Boolean scuolaCalcio) {
        this.scuolaCalcio = scuolaCalcio;
        return this;
    }

    public String getPrezziScuolaCalcio() {
        return prezziScuolaCalcio;
    }

    public Societa setPrezziScuolaCalcio(String prezziScuolaCalcio) {
        this.prezziScuolaCalcio = prezziScuolaCalcio;
        return this;
    }

    public List<Campionato> getCampionati() {
        return campionati;
    }

    public Societa setCampionati(List<Campionato> campionati) {
        this.campionati = campionati;
        return this;
    }

    public String getTestoRicerca() {
        return testoRicerca;
    }

    public Societa setTestoRicerca(String testoRicerca) {
        this.testoRicerca = testoRicerca;
        return this;
    }
}
