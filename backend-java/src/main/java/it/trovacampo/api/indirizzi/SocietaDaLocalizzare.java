package it.trovacampo.api.indirizzi;

/**
 * Una società di cui si cerca il campo: quello che il Registro RASD (e quindi
 * un {@code Prospect}) ne sa, senza l'indirizzo. Il registro non dà la
 * matricola FIGC: l'abbinamento passa sempre dal nome e dal comune.
 *
 * @param denominazione come la scrive il registro, forma giuridica compresa
 *     ("ASSOCIAZIONE SPORTIVA DILETTANTISTICA VIS SUBIACO")
 * @param comune il comune della sede legale
 * @param codiceFiscale la chiave della società nel registro; può mancare
 *     per una ricerca di prova
 */
public record SocietaDaLocalizzare(String denominazione, String comune, String codiceFiscale) {

    public SocietaDaLocalizzare(String denominazione, String comune) {
        this(denominazione, comune, null);
    }
}
