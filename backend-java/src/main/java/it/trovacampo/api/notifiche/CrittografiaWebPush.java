package it.trovacampo.api.notifiche;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * La crittografia del Web Push, con le sole classi del JDK.
 *
 * <p>Due pezzi:
 *
 * <ul>
 *   <li>il contenuto della notifica si cifra per il browser che la riceve
 *       (RFC 8291, con la codifica {@code aes128gcm} della RFC 8188): il
 *       servizio push di Google, Mozilla o Apple la inoltra senza poterla
 *       leggere;
 *   <li>la richiesta al servizio push si firma con le chiavi VAPID (RFC 8292,
 *       un JWT ES256): il servizio accetta solo le notifiche firmate dalla
 *       chiave con cui il browser si è iscritto.
 * </ul>
 *
 * <p>Niente librerie esterne di proposito: servirebbero BouncyCastle e un
 * client HTTP in più per una cinquantina di righe, e così ogni passaggio si
 * legge qui e si prova con i vettori della RFC.
 */
public final class CrittografiaWebPush {

    /** Un punto P-256 non compresso: 0x04, poi x e y da 32 byte. */
    static final int LUNGHEZZA_CHIAVE_PUBBLICA = 65;
    static final int LUNGHEZZA_AUTH = 16;
    private static final int LUNGHEZZA_SALE = 16;
    /**
     * Un record solo, e la notifica ci sta per forza: i servizi push accettano
     * al massimo 4096 byte di corpo, intestazione compresa.
     */
    private static final int DIMENSIONE_RECORD = 4096;
    /** Intestazione (86 byte) e tag di GCM (16) lasciano questo al testo. */
    public static final int MASSIMO_CONTENUTO = 3800;

    private static final SecureRandom CASO = new SecureRandom();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DA_BASE64URL = Base64.getUrlDecoder();

    private CrittografiaWebPush() {}

    /** Una coppia di chiavi P-256, per VAPID o per una singola notifica. */
    public static KeyPair nuoveChiavi() {
        try {
            KeyPairGenerator generatore = KeyPairGenerator.getInstance("EC");
            generatore.initialize(new ECGenParameterSpec("secp256r1"), CASO);
            return generatore.generateKeyPair();
        } catch (GeneralSecurityException eccezione) {
            throw new IllegalStateException("P-256 non disponibile nel JDK", eccezione);
        }
    }

    /**
     * Cifra {@code contenuto} per il browser con chiave {@code p256dh} e
     * segreto {@code auth} (quelli dell'iscrizione, in base64url). Il
     * risultato è il corpo della richiesta al servizio push.
     */
    public static byte[] cifra(byte[] contenuto, String p256dh, String auth) {
        return cifra(contenuto, decodifica(p256dh), decodifica(auth), nuoveChiavi(), sale());
    }

    /** Con chiavi effimere e sale dati: per provare i vettori della RFC 8291. */
    static byte[] cifra(byte[] contenuto, byte[] chiaveBrowser, byte[] auth, KeyPair effimere, byte[] sale) {
        if (contenuto.length > MASSIMO_CONTENUTO) {
            throw new IllegalArgumentException("Notifica troppo lunga: " + contenuto.length + " byte");
        }
        try {
            byte[] pubblicaBrowser = verificaChiavePubblica(chiaveBrowser);
            if (auth.length != LUNGHEZZA_AUTH) {
                throw new IllegalArgumentException("Segreto auth di " + auth.length + " byte invece di 16");
            }
            byte[] pubblicaEffimera = pubblicaGrezza((ECPublicKey) effimere.getPublic());

            KeyAgreement accordo = KeyAgreement.getInstance("ECDH");
            accordo.init(effimere.getPrivate());
            accordo.doPhase(chiavePubblica(pubblicaBrowser), true);
            byte[] segretoEcdh = accordo.generateSecret();

            // RFC 8291, 3.4: dal segreto ECDH e da auth la chiave d'ingresso,
            // legata a tutte e due le chiavi pubbliche.
            byte[] prkChiave = hmac(auth, segretoEcdh);
            byte[] infoChiave =
                    concatena(
                            "WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                            pubblicaBrowser,
                            pubblicaEffimera,
                            new byte[] {1});
            byte[] ikm = hmac(prkChiave, infoChiave);

            // RFC 8188, 2.2 e 2.3: chiave di contenuto e nonce, dal sale.
            byte[] prk = hmac(sale, ikm);
            byte[] cek =
                    Arrays.copyOf(
                            hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)),
                            16);
            byte[] nonce =
                    Arrays.copyOf(
                            hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);

            // Un record solo, quindi l'ultimo: delimitatore 0x02, niente riempimento.
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] cifrato = aes.doFinal(concatena(contenuto, new byte[] {2}));

            ByteBuffer corpo = ByteBuffer.allocate(LUNGHEZZA_SALE + 4 + 1 + pubblicaEffimera.length + cifrato.length);
            corpo.put(sale).putInt(DIMENSIONE_RECORD).put((byte) pubblicaEffimera.length).put(pubblicaEffimera);
            corpo.put(cifrato);
            return corpo.array();
        } catch (GeneralSecurityException eccezione) {
            throw new IllegalArgumentException("Chiave del browser non valida", eccezione);
        }
    }

    /**
     * L'intestazione {@code Authorization} per il servizio push di
     * {@code destinatario} (schema e host dell'endpoint): un JWT firmato con la
     * chiave privata VAPID, valido fino a {@code scadenza}, e la chiave
     * pubblica con cui controllarlo.
     *
     * @param contatto chi avvisare se le notifiche danno problemi: un https:// o un mailto:
     */
    public static String autorizzazione(ChiaviVapid chiavi, String destinatario, String contatto, Instant scadenza) {
        String intestazione = testoBase64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String dati =
                testoBase64(
                        "{\"aud\":\""
                                + json(destinatario)
                                + "\",\"exp\":"
                                + scadenza.getEpochSecond()
                                + ",\"sub\":\""
                                + json(contatto)
                                + "\"}");
        String daFirmare = intestazione + "." + dati;
        try {
            // Il formato P1363 è r||s da 32 byte ciascuno, quello che vuole il
            // JWT; il DER che darebbe "SHA256withECDSA" andrebbe smontato.
            Signature firma = Signature.getInstance("SHA256withECDSAinP1363Format");
            firma.initSign(chiavi.privata());
            firma.update(daFirmare.getBytes(StandardCharsets.US_ASCII));
            String jwt = daFirmare + "." + BASE64URL.encodeToString(firma.sign());
            return "vapid t=" + jwt + ", k=" + chiavi.pubblicaBase64();
        } catch (GeneralSecurityException eccezione) {
            throw new IllegalStateException("Firma VAPID non riuscita", eccezione);
        }
    }

    /** Una chiave pubblica P-256 nel formato che usano browser e VAPID: 65 byte, 0x04 x y. */
    public static byte[] pubblicaGrezza(ECPublicKey chiave) {
        ECPoint punto = chiave.getW();
        return concatena(new byte[] {4}, a32Byte(punto.getAffineX()), a32Byte(punto.getAffineY()));
    }

    /** La chiave privata nuda, 32 byte: il formato delle chiavi VAPID di tutte le librerie. */
    public static byte[] privataGrezza(ECPrivateKey chiave) {
        return a32Byte(chiave.getS());
    }

    static ECPublicKey chiavePubblica(byte[] grezza) throws GeneralSecurityException {
        verificaChiavePubblica(grezza);
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(grezza, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(grezza, 33, 65));
        ECParameterSpec curva = p256();
        // KeyFactory accetta anche un punto fuori dalla curva: lo si scopre
        // solo al primo invio. Meglio rifiutarlo all'iscrizione.
        if (!sullaCurva(x, y, curva)) {
            throw new IllegalArgumentException("Il punto non sta sulla curva P-256");
        }
        return (ECPublicKey)
                KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), curva));
    }

    /** y² = x³ + ax + b (mod p). */
    private static boolean sullaCurva(BigInteger x, BigInteger y, ECParameterSpec parametri) {
        BigInteger p = ((ECFieldFp) parametri.getCurve().getField()).getP();
        if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            return false;
        }
        BigInteger a = parametri.getCurve().getA();
        BigInteger b = parametri.getCurve().getB();
        BigInteger sinistra = y.multiply(y).mod(p);
        BigInteger destra = x.pow(3).add(a.multiply(x)).add(b).mod(p);
        return sinistra.equals(destra);
    }

    static ECPrivateKey chiavePrivata(byte[] grezza) throws GeneralSecurityException {
        if (grezza.length != 32) {
            throw new IllegalArgumentException("Chiave privata di " + grezza.length + " byte invece di 32");
        }
        return (ECPrivateKey)
                KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, grezza), p256()));
    }

    /**
     * Controlla una chiave pubblica di un browser senza usarla: 65 byte, un
     * punto della curva. Serve alla validazione dell'iscrizione, prima di
     * salvarla.
     */
    public static boolean chiavePubblicaValida(String base64url) {
        try {
            chiavePubblica(decodifica(base64url));
            return true;
        } catch (IllegalArgumentException | GeneralSecurityException eccezione) {
            return false;
        }
    }

    public static boolean authValido(String base64url) {
        try {
            return decodifica(base64url).length == LUNGHEZZA_AUTH;
        } catch (IllegalArgumentException eccezione) {
            return false;
        }
    }

    /** Il base64url dei browser, con o senza i "=" in fondo. */
    public static byte[] decodifica(String base64url) {
        if (base64url == null) {
            throw new IllegalArgumentException("Chiave mancante");
        }
        return DA_BASE64URL.decode(base64url.strip().replace("=", ""));
    }

    public static String codifica(byte[] dati) {
        return BASE64URL.encodeToString(dati);
    }

    private static byte[] verificaChiavePubblica(byte[] grezza) {
        if (grezza.length != LUNGHEZZA_CHIAVE_PUBBLICA || grezza[0] != 4) {
            throw new IllegalArgumentException("Chiave pubblica P-256 non valida");
        }
        return grezza;
    }

    private static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters parametri = AlgorithmParameters.getInstance("EC");
        parametri.init(new ECGenParameterSpec("secp256r1"));
        return parametri.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] sale() {
        byte[] sale = new byte[LUNGHEZZA_SALE];
        CASO.nextBytes(sale);
        return sale;
    }

    private static byte[] hmac(byte[] chiave, byte[] dati) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chiave, "HmacSHA256"));
        return mac.doFinal(dati);
    }

    /** BigInteger toglie gli zeri in testa e aggiunge un byte di segno: qui sempre 32 byte. */
    private static byte[] a32Byte(BigInteger numero) {
        byte[] byteNumero = numero.toByteArray();
        byte[] risultato = new byte[32];
        int da = Math.max(0, byteNumero.length - 32);
        int quanti = byteNumero.length - da;
        System.arraycopy(byteNumero, da, risultato, 32 - quanti, quanti);
        return risultato;
    }

    private static byte[] concatena(byte[]... parti) {
        int lunghezza = 0;
        for (byte[] parte : parti) {
            lunghezza += parte.length;
        }
        byte[] risultato = new byte[lunghezza];
        int posizione = 0;
        for (byte[] parte : parti) {
            System.arraycopy(parte, 0, risultato, posizione, parte.length);
            posizione += parte.length;
        }
        return risultato;
    }

    private static String testoBase64(String testo) {
        return BASE64URL.encodeToString(testo.getBytes(StandardCharsets.UTF_8));
    }

    /** Destinatario e contatto vengono dalla configurazione, ma un " li romperebbe. */
    private static String json(String testo) {
        return testo.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
