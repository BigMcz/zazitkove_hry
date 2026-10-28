package cz.mibcon.zazitky.session;

import cz.mibcon.zazitky.common.ApiException;
import org.springframework.http.HttpStatus;

/** Missing, malformed or unknown {@code X-Session-Token}. */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "invalid-token", "Neplatný token relace",
                "Rozehraná hra nebyla nalezena. Začni prosím novou hru.");
    }
}
