package cz.mibcon.zazitky.common;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Příliš mnoho pokusů",
                "Zkoušíš to moc často. Počkej prosím minutu a zkus to znovu.");
    }
}
