package it.trovacampo.api.notifiche;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Le notifiche push ({@code trovacampo.notifiche.*}): il contatto della firma VAPID,
 * le chiavi VAPID da ambiente (vuote: le crea il backend e le tiene in Mongo) e
 * gli unici host a cui si mandano notifiche, sottodomini compresi.
 */
@ConfigurationProperties("trovacampo.notifiche")
@Validated
public record ProprietaNotifiche(
        @DefaultValue("https://trovacampo.footballer.it") String contatto,
        @DefaultValue Vapid vapid,
        @NotEmpty List<String> serviziPush) {

    public record Vapid(@DefaultValue("") String pubblica, @DefaultValue("") String privata) {}
}
