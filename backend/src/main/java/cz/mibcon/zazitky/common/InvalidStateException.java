package cz.mibcon.zazitky.common;

import org.springframework.http.HttpStatus;

/** The action is not allowed in the current state, e.g. answering a station that is not active. */
public class InvalidStateException extends ApiException {

    public InvalidStateException(String detail) {
        super(HttpStatus.CONFLICT, "invalid-state", "Neplatný stav hry", detail);
    }
}
