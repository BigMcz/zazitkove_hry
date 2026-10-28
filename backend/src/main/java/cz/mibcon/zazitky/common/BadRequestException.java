package cz.mibcon.zazitky.common;

import org.springframework.http.HttpStatus;

/** Invalid input that Bean Validation cannot express (e.g. a taken user name, a wrong current password). */
public class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "validation", "Neplatný požadavek", detail);
    }
}
