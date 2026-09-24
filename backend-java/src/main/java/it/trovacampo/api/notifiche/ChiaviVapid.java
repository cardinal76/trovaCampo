package it.trovacampo.api.notifiche;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * Le chiavi VAPID con cui TrovaCampo firma le notifiche. La pubblica la
 * conosce ogni browser iscritto; la privata non esce mai dal backend e dal
 * suo database, e {@link #toString()} non la stampa.
 */
public record ChiaviVapid(ECPublicKey pubblica, ECPrivateKey privata) {

    /** Dai due valori base64url, nel formato di tutte le librerie Web Push. */
    public static ChiaviVapid da(String pubblica, String privata) {
        try {
            return new ChiaviVapid(
                    CrittografiaWebPush.chiavePubblica(CrittografiaWebPush.decodifica(pubblica)),
                    CrittografiaWebPush.chiavePrivata(CrittografiaWebPush.decodifica(privata)));
        } catch (GeneralSecurityException eccezione) {
            throw new IllegalArgumentException("Chiavi VAPID non valide", eccezione);
        }
    }

    public static ChiaviVapid nuove() {
        KeyPair coppia = CrittografiaWebPush.nuoveChiavi();
        return new ChiaviVapid((ECPublicKey) coppia.getPublic(), (ECPrivateKey) coppia.getPrivate());
    }

    /** Quella che il browser vuole come applicationServerKey. */
    public String pubblicaBase64() {
        return CrittografiaWebPush.codifica(CrittografiaWebPush.pubblicaGrezza(pubblica));
    }

    String privataBase64() {
        return CrittografiaWebPush.codifica(CrittografiaWebPush.privataGrezza(privata));
    }

    @Override
    public String toString() {
        return "ChiaviVapid[pubblica=" + pubblicaBase64() + "]";
    }
}
