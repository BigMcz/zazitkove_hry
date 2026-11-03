package cz.mibcon.zazitky.cardprint;

import cz.mibcon.zazitky.common.ApiException;
import org.springframework.http.HttpStatus;

/** Another print is being generated and did not finish in time. */
public class PrintBusyException extends ApiException {

    public PrintBusyException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "print-busy", "Tisk je obsazený",
                "Právě se generuje jiný tisk, zkuste to za chvíli.");
    }
}
