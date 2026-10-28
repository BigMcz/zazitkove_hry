package cz.mibcon.zazitky.session;

import cz.mibcon.zazitky.common.ApiException;
import org.springframework.http.HttpStatus;

public class SessionExpiredException extends ApiException {

    public SessionExpiredException() {
        super(HttpStatus.GONE, "session-expired", "Hra vypršela",
                "Platnost rozehrané hry vypršela. Začni prosím novou hru.");
    }
}
