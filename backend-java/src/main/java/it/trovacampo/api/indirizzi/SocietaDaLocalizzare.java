package it.trovacampo.api.indirizzi;

/**
 * Una società di cui si cerca il campo: quello che il Registro RASD (e poi la
 * collection {@code prospect}) ne sa, senza l'indirizzo.
 *
 * @param denominazione come la scrive il registro, forma giuridica compresa
 *     ("A.S.D. ROMULEA", "VIS AURELIA S.S.D. A R.L.")
 * @param comune il comune della sede
 * @param codiceFiscale la chiave della società nel registro; può mancare
 *     per una ricerca di prova
 * @param codiceFigc la matricola FIGC, quando c'è: è il solo abbinamento
 *     che non passa dal nome
 */
public record SocietaDaLocalizzare(
        String denominazione, String comune, String codiceFiscale, String codiceFigc) {

    public SocietaDaLocalizzare(String denominazione, String comune) {
        this(denominazione, comune, null, null);
    }
}
