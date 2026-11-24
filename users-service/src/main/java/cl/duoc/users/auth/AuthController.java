package cl.duoc.users.auth;

import cl.duoc.users.user.AppUser;
import cl.duoc.users.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordRecoveryService recovery;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            PasswordRecoveryService recovery) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.recovery = recovery;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredUser register(@Valid @RequestBody RegistrationRequest request) {
        try {
            AppUser user = users.saveAndFlush(new AppUser(request.username(), request.email(),
                    passwordEncoder.encode(request.password()), cl.duoc.users.user.Role.USER));
            return new RegisteredUser(user.getId(), user.getUsername(), user.getEmail(), user.getRole().name());
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
        }
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        return new TokenResponse(jwtService.issue(user), "Bearer");
    }

    @PostMapping("/recover")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RecoveryResponse recover(@Valid @RequestBody RecoveryRequest request) {
        recovery.request(request.email());
        return new RecoveryResponse("If the account exists, recovery instructions will be sent.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        if (!recovery.reset(request.token(), request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired recovery token");
        }
    }

    @GetMapping("/me")
    public UserIdentity me(@AuthenticationPrincipal AppUser user) {
        return new UserIdentity(user.getId(), user.getUsername(), user.getRole().name());
    }

    public record RegistrationRequest(@NotBlank @Size(max = 50) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
            @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter")
            @Pattern(regexp = ".*[0-9].*", message = "Password must contain a digit")
            @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "Password must contain a special character")
            String password) {
    }

    public record RegisteredUser(Long id, String username, String email, String role) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RecoveryRequest(@NotBlank @Email @Size(max = 254) String email) { }

    public record ResetPasswordRequest(@NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
            @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter")
            @Pattern(regexp = ".*[0-9].*", message = "Password must contain a digit")
            @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "Password must contain a special character")
            String password) { }

    public record RecoveryResponse(String message) { }

    public record TokenResponse(String token, String tokenType) {
    }

    public record UserIdentity(Long id, String username, String role) {
    }
}
