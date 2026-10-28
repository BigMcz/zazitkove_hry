package cz.mibcon.zazitky.accesscode;

import cz.mibcon.zazitky.common.ApiException;
import org.springframework.http.HttpStatus;

public class CodeExpiredException extends ApiException {

    public CodeExpiredException() {
        super(HttpStatus.GONE, "code-expired", "Kód vypršel",
                "Platnost tohoto kódu už vypršela. Pro novou hru si prosím kup nový kód.");
    }
}
