package cz.mibcon.zazitky.session;

import java.net.URI;
import java.util.UUID;

import cz.mibcon.zazitky.accesscode.CodeGuessGuard;
import cz.mibcon.zazitky.common.RateLimiter;
import cz.mibcon.zazitky.session.SessionDtos.AnswerResult;
import cz.mibcon.zazitky.session.SessionDtos.HintsResult;
import cz.mibcon.zazitky.session.SessionDtos.SessionState;
import cz.mibcon.zazitky.session.SessionDtos.StartSessionRequest;
import cz.mibcon.zazitky.session.SessionDtos.SubmitAnswerRequest;
import cz.mibcon.zazitky.session.SessionDtos.SyncRequest;
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
    private final CodeGuessGuard guessGuard;
    private final RateLimiter actionRateLimiter;

    public SessionController(SessionService sessionService, CodeGuessGuard guessGuard,
            @Qualifier("sessionActionRateLimiter") RateLimiter actionRateLimiter) {
        this.sessionService = sessionService;
        this.guessGuard = guessGuard;
        this.actionRateLimiter = actionRateLimiter;
    }

    /** 201 for a new game, 200 when the code continues its existing game; both include the token. */
    @PostMapping
    public ResponseEntity<SessionState> start(@Valid @RequestBody StartSessionRequest request, HttpServletRequest http) {
        StartResult result = guessGuard.attempt(http.getRemoteAddr(),
                () -> sessionService.start(request.code(), request.teamName(), request.language()));
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

    @PostMapping("/current/sync")
    public SessionState sync(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody SyncRequest request) {
        return sessionService.sync(limited(token), request.events());
    }

    @PostMapping("/current/stations/{stationId}/answers")
    public AnswerResult submitAnswer(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @PathVariable UUID stationId, @Valid @RequestBody SubmitAnswerRequest request) {
        return sessionService.submitAnswer(limited(token), stationId, request.answer());
    }

    @PostMapping("/current/stations/{stationId}/hints")
    public HintsResult revealHint(@RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @PathVariable UUID stationId) {
        return sessionService.revealHint(limited(token), stationId);
    }

    @PostMapping("/current/abandon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        sessionService.abandon(parseToken(token));
    }

    /** Actions that write to the game: answers could be guessed online and every attempt is stored. */
    private UUID limited(String token) {
        UUID parsed = parseToken(token);
        actionRateLimiter.acquire(parsed.toString());
        return parsed;
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
