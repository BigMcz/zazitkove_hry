package cz.mibcon.zazitky.session;

import java.net.URI;
import java.util.UUID;

import cz.mibcon.zazitky.common.RateLimiter;
import cz.mibcon.zazitky.session.SessionDtos.AnswerResult;
import cz.mibcon.zazitky.session.SessionDtos.HintsResult;
import cz.mibcon.zazitky.session.SessionDtos.SessionState;
import cz.mibcon.zazitky.session.SessionDtos.StartSessionRequest;
import cz.mibcon.zazitky.session.SessionDtos.SubmitAnswerRequest;
import cz.mibcon.zazitky.session.SessionService.StartResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The player's session is identified by the {@value #TOKEN_HEADER} header, never by the URL. */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    static final String TOKEN_HEADER = "X-Session-Token";

    private final SessionService sessionService;
    private final RateLimiter codeRateLimiter;

    public SessionController(SessionService sessionService,
            @Qualifier("accessCodeRateLimiter") RateLimiter codeRateLimiter) {
        this.sessionService = sessionService;
        this.codeRateLimiter = codeRateLimiter;
    }

    /** 201 for a new game, 200 when the code continues its existing game; both include the token. */
    @PostMapping
    public ResponseEntity<SessionState> start(@Valid @RequestBody StartSessionRequest request, HttpServletRequest http) {
        codeRateLimiter.acquire(http.getRemoteAddr());
        StartResult result = sessionService.start(request.code(), request.teamName());
        if (!result.created()) {
            return ResponseEntity.ok(result.state());
        }
        return ResponseEntity.created(URI.create("/api/v1/sessions/current")).body(result.state());
    }

    @GetMapping("/current")
    public SessionState current(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return sessionService.getState(parseToken(token));
    }

    @GetMapping("/current/package")
    public SessionPackage gamePackage(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        return sessionService.getPackage(parseToken(token));
    }

    @PostMapping("/current/stations/{stationId}/answers")
    public AnswerResult submitAnswer(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @PathVariable UUID stationId, @Valid @RequestBody SubmitAnswerRequest request) {
        return sessionService.submitAnswer(parseToken(token), stationId, request.answer());
    }

    @PostMapping("/current/stations/{stationId}/hints")
    public HintsResult revealHint(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @PathVariable UUID stationId) {
        return sessionService.revealHint(parseToken(token), stationId);
    }

    @PostMapping("/current/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        sessionService.abandon(parseToken(token));
    }

    private static UUID parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException();
        }
        try {
            return UUID.fromString(token.strip());
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }
}
