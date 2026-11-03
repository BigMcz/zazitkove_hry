package cz.mibcon.zazitky.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Public address of the app for the links printed on the cards (QR code, CSV for the printer). */
@Component
public class PublicUrl {

    private final String configured;

    public PublicUrl(@Value("${app.public-url:}") String configured) {
        this.configured = configured;
    }

    /** Without a configured public URL (local development) the links point to the server of the current request. */
    public String base() {
        return configured.isBlank() ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() : configured;
    }
}
