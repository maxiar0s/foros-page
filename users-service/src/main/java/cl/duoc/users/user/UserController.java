package cl.duoc.users.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserResponse> list() { return users.findAll().stream().map(UserResponse::from).toList(); }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable("id") Long id) { return UserResponse.from(find(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        try {
            AppUser saved = users.saveAndFlush(new AppUser(request.username(), request.email(),
                    passwordEncoder.encode(request.password()), request.role()));
            return UserResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
        }
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable("id") Long id, @Valid @RequestBody UpdateUserRequest request) {
        AppUser user = find(id);
        user.updateProfile(request.username(), request.email(), request.role());
        try {
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) { users.delete(find(id)); }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public record CreateUserRequest(@NotBlank @Size(max = 50) String username,
                                    @NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Size(min = 12, max = 72) String password,
                                    @NotNull Role role) { }
    public record UpdateUserRequest(@NotBlank @Size(max = 50) String username,
                                    @NotBlank @Email @Size(max = 254) String email,
                                    @NotNull Role role) { }
    public record UserResponse(Long id, String username, String email, Role role) {
        static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
        }
    }
}
