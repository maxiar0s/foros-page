package cl.duoc.users.auth;

import cl.duoc.users.user.AppUser;
import cl.duoc.users.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final JavaMailSender mailSender;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final String from;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(UserRepository users, PasswordResetTokenRepository tokens,
            JavaMailSender mailSender,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            @Value("${app.recovery.from:noreply@example.test}") String from,
            @Value("${app.recovery.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.users = users;
        this.tokens = tokens;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public void request(String email) {
        users.findByEmail(email).ifPresent(user -> issue(user));
    }

    private void issue(AppUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.deleteByUser_Id(user.getId());
        tokens.save(new PasswordResetToken(user, sha256(rawToken), Instant.now().plus(TOKEN_LIFETIME)));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Recuperación de contraseña");
        message.setText("Para elegir una nueva contraseña, abre este enlace antes de 30 minutos:\n"
                + frontendUrl + "/#resetToken=" + rawToken);
        try {
            mailSender.send(message);
        } catch (MailException ignored) {
            // Never include account data or reset tokens in logs or responses.
        }
    }

    @Transactional
    public boolean reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokens.findForUpdate(sha256(rawToken)).orElse(null);
        if (token == null || !token.getExpiresAt().isAfter(Instant.now())) return false;
        AppUser user = users.findById(token.getUser().getId()).orElse(null);
        if (user == null) return false;
        user.changePassword(passwordEncoder.encode(newPassword));
        users.save(user);
        tokens.delete(token);
        return true;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
