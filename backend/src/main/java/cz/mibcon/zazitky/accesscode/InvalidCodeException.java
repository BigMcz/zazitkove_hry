package cz.mibcon.zazitky.accesscode;

import cz.mibcon.zazitky.common.ApiException;
import org.springframework.http.HttpStatus;

/** Unknown or cancelled code, or a code of a game that is not published. */
public class InvalidCodeException extends ApiException {

    public InvalidCodeException() {
        super(HttpStatus.NOT_FOUND, "invalid-code", "Neplatný kód",
                "Tento kód neznáme. Zkontroluj ho prosím podle kartičky.");
    }
}
