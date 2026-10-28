package cz.mibcon.zazitky.accesscode;

import cz.mibcon.zazitky.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access-codes")
public class AccessCodeController {

    private final AccessCodeService accessCodeService;
    private final RateLimiter rateLimiter;

    public AccessCodeController(AccessCodeService accessCodeService,
            @Qualifier("accessCodeRateLimiter") RateLimiter rateLimiter) {
        this.accessCodeService = accessCodeService;
        this.rateLimiter = rateLimiter;
    }

    /** POST, so that the code does not end up in URLs and access logs. */
    @PostMapping("/check")
    public CodeCheckDto check(@Valid @RequestBody CheckCodeRequest request, HttpServletRequest http) {
        rateLimiter.acquire(http.getRemoteAddr());
        return accessCodeService.check(request.code());
    }

    public record CheckCodeRequest(@NotBlank @Size(max = 30) String code) {
    }
}
