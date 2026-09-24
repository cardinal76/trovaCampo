package it.trovacampo.api.notifiche;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Il lato del browser, per i test: le sue chiavi, e la decifratura di un
 * messaggio aes128gcm scritta a parte, seguendo la RFC 8291 dal punto di
 * vista di chi riceve. Se cifratura e decifratura sbagliassero allo stesso
 * modo, il vettore della RFC in {@link CrittografiaWebPushTest} se ne
 * accorgerebbe comunque.
 */
final class Browser {

    private final KeyPair chiavi = CrittografiaWebPush.nuoveChiavi();
    private final byte[] auth = new byte[16];

    Browser() {
        new SecureRandom().nextBytes(auth);
    }

    String p256dh() {
        return CrittografiaWebPush.codifica(CrittografiaWebPush.pubblicaGrezza((ECPublicKey) chiavi.getPublic()));
    }

    String auth() {
        return CrittografiaWebPush.codifica(auth);
    }

    String decifra(byte[] messaggio) {
        try {
            ByteBuffer lettore = ByteBuffer.wrap(messaggio);
            byte[] sale = new byte[16];
            lettore.get(sale);
            int dimensioneRecord = lettore.getInt();
            byte[] chiaveMittente = new byte[lettore.get() & 0xff];
            lettore.get(chiaveMittente);
            byte[] cifrato = new byte[lettore.remaining()];
            lettore.get(cifrato);
            if (dimensioneRecord < cifrato.length) {
                throw new IllegalStateException("Più di un record");
            }

            KeyAgreement accordo = KeyAgreement.getInstance("ECDH");
            accordo.init(chiavi.getPrivate());
            accordo.doPhase(CrittografiaWebPush.chiavePubblica(chiaveMittente), true);
            byte[] segreto = accordo.generateSecret();

            byte[] mia = CrittografiaWebPush.pubblicaGrezza((ECPublicKey) chiavi.getPublic());
            ByteBuffer info = ByteBuffer.allocate(14 + 65 + 65 + 1);
            info.put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII)).put(mia).put(chiaveMittente).put((byte) 1);
            byte[] ikm = hmac(hmac(auth, segreto), info.array());
            byte[] prk = hmac(sale, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(StandardCharsets.US_ASCII)), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(StandardCharsets.US_ASCII)), 12);

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] chiaro = aes.doFinal(cifrato);
            // Via il riempimento: zeri in fondo, poi il delimitatore 0x02 dell'ultimo record.
            int fine = chiaro.length - 1;
            while (fine >= 0 && chiaro[fine] == 0) {
                fine--;
            }
            if (fine < 0 || chiaro[fine] != 2) {
                throw new IllegalStateException("Delimitatore dell'ultimo record mancante");
            }
            return new String(chiaro, 0, fine, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException eccezione) {
            throw new IllegalStateException(eccezione);
        }
    }

    private static byte[] hmac(byte[] chiave, byte[] dati) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chiave, "HmacSHA256"));
        return mac.doFinal(dati);
    }
}
