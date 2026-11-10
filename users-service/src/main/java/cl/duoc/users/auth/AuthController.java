package cl.duoc.users.auth;

import cl.duoc.users.user.AppUser;
import cl.duoc.users.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AppUser user = users.findByUsername(request.username())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        return new TokenResponse(jwtService.issue(user), "Bearer");
    }

    @GetMapping("/me")
    public UserIdentity me(@AuthenticationPrincipal AppUser user) {
        return new UserIdentity(user.getId(), user.getUsername(), user.getRole().name());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String token, String tokenType) {
    }

    public record UserIdentity(Long id, String username, String role) {
    }
}
