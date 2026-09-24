package it.trovacampo.api.notifiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CrittografiaWebPushTest {

    private static byte[] b64(String testo) {
        return CrittografiaWebPush.decodifica(testo);
    }

    /**
     * L'esempio dell'appendice A della RFC 8291, byte per byte: con le stesse
     * chiavi e lo stesso sale deve uscire lo stesso messaggio cifrato. È la
     * prova che un browser vero lo saprà decifrare.
     */
    @Test
    void riproduceLEsempioDellaRfc8291() throws Exception {
        byte[] privataServer = b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
        byte[] pubblicaServer =
                b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        KeyPair effimere =
                new KeyPair(
                        CrittografiaWebPush.chiavePubblica(pubblicaServer),
                        CrittografiaWebPush.chiavePrivata(privataServer));

        byte[] cifrato =
                CrittografiaWebPush.cifra(
                        "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                        b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                        b64("BTBZMqHH6r4Tts7J_aSIgg"),
                        effimere,
                        b64("DGv6ra1nlYgDCS1FRnbzlw"));

        assertThat(CrittografiaWebPush.codifica(cifrato))
                .isEqualTo(
                        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    void ilBrowserDecifraQuelloCheCifriamo() {
        Browser browser = new Browser();
        String notifica = "{\"titolo\":\"Tra un'ora: BOREALE – VIGOR PERCONTI\"}";

        byte[] cifrato =
                CrittografiaWebPush.cifra(notifica.getBytes(StandardCharsets.UTF_8), browser.p256dh(), browser.auth());

        assertThat(browser.decifra(cifrato)).isEqualTo(notifica);
        // Due invii dello stesso testo non si somigliano: chiavi e sale nuovi ogni volta.
        byte[] ancora =
                CrittografiaWebPush.cifra(notifica.getBytes(StandardCharsets.UTF_8), browser.p256dh(), browser.auth());
        assertThat(Arrays.equals(cifrato, ancora)).isFalse();
    }

    @Test
    void laFirmaVapidSiVerificaConLaChiavePubblica() throws Exception {
        ChiaviVapid chiavi = ChiaviVapid.nuove();
        Instant scadenza = Instant.parse("2026-09-06T21:00:00Z");

        String intestazione =
                CrittografiaWebPush.autorizzazione(
                        chiavi, "https://fcm.googleapis.com", "https://trovacampo.footballer.it", scadenza);

        assertThat(intestazione).startsWith("vapid t=").endsWith(", k=" + chiavi.pubblicaBase64());
        String jwt = intestazione.substring("vapid t=".length(), intestazione.indexOf(','));
        String[] parti = jwt.split("\\.");
        assertThat(parti).hasSize(3);

        ObjectMapper json = new ObjectMapper();
        JsonNode testa = json.readTree(b64(parti[0]));
        JsonNode dati = json.readTree(b64(parti[1]));
        assertThat(testa.get("alg").asText()).isEqualTo("ES256");
        assertThat(dati.get("aud").asText()).isEqualTo("https://fcm.googleapis.com");
        assertThat(dati.get("exp").asLong()).isEqualTo(scadenza.getEpochSecond());
        assertThat(dati.get("sub").asText()).isEqualTo("https://trovacampo.footballer.it");

        // ES256 vuole r||s da 64 byte, non il DER di Java.
        byte[] firma = b64(parti[2]);
        assertThat(firma).hasSize(64);
        Signature verifica = Signature.getInstance("SHA256withECDSAinP1363Format");
        ECPublicKey pubblica =
                CrittografiaWebPush.chiavePubblica(CrittografiaWebPush.decodifica(chiavi.pubblicaBase64()));
        verifica.initVerify(pubblica);
        verifica.update((parti[0] + "." + parti[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifica.verify(firma)).isTrue();
    }

    @Test
    void leChiaviVapidSiRileggonoDalFormatoSalvato() {
        ChiaviVapid chiavi = ChiaviVapid.nuove();

        ChiaviVapid rilette = ChiaviVapid.da(chiavi.pubblicaBase64(), chiavi.privataBase64());

        assertThat(rilette.pubblicaBase64()).isEqualTo(chiavi.pubblicaBase64());
        assertThat(b64(chiavi.pubblicaBase64())).hasSize(65);
        assertThat(b64(chiavi.privataBase64())).hasSize(32);
        // La privata non finisce nei log per sbaglio.
        assertThat(chiavi.toString()).doesNotContain(chiavi.privataBase64());
    }

    @Test
    void chiaviDelBrowserNonValideSiRiconoscono() {
        Browser browser = new Browser();

        assertThat(CrittografiaWebPush.chiavePubblicaValida(browser.p256dh())).isTrue();
        assertThat(CrittografiaWebPush.authValido(browser.auth())).isTrue();
        assertThat(CrittografiaWebPush.chiavePubblicaValida("AAAA")).isFalse();
        assertThat(CrittografiaWebPush.chiavePubblicaValida("non è base64")).isFalse();
        // 65 byte che cominciano per 0x04 ma non stanno sulla curva.
        byte[] fuoriCurva = new byte[65];
        fuoriCurva[0] = 4;
        fuoriCurva[64] = 1;
        assertThat(CrittografiaWebPush.chiavePubblicaValida(CrittografiaWebPush.codifica(fuoriCurva))).isFalse();
        assertThat(CrittografiaWebPush.authValido(CrittografiaWebPush.codifica(new byte[8]))).isFalse();
    }

    @Test
    void unaNotificaTroppoLungaNonSiCifra() {
        Browser browser = new Browser();

        assertThatThrownBy(() -> CrittografiaWebPush.cifra(new byte[5000], browser.p256dh(), browser.auth()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
