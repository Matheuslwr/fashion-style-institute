package br.com.fsistudio.store.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import br.com.fsistudio.store.order.CustomerOrderView;
import br.com.fsistudio.store.order.StoreOrderRepository;
import br.com.fsistudio.store.order.StoreOrderService;

@Service
public class CustomerAccountService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerAccountService.class);
    private static final Duration EMAIL_CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration EMAIL_CODE_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final CustomerAccountRepository accounts;
    private final CustomerLoginAttemptLimiter loginLimiter;
    private final CustomerAccountSessionRepository sessions;
    private final CustomerAccountEmailCodeRepository emailCodes;
    private final StoreOrderRepository orders;
    private final StoreOrderService orderService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String emailFrom;
    private final String emailHost;
    private final boolean emailUsernameConfigured;
    private final boolean emailPasswordConfigured;

    public CustomerAccountService(
        CustomerAccountRepository accounts,
        CustomerLoginAttemptLimiter loginLimiter,
        CustomerAccountSessionRepository sessions,
        CustomerAccountEmailCodeRepository emailCodes,
        StoreOrderRepository orders,
        StoreOrderService orderService,
        PasswordEncoder passwordEncoder,
        JavaMailSender mailSender,
        @Value("${app.mail.from:}") String emailFrom,
        @Value("${spring.mail.host:}") String emailHost,
        @Value("${spring.mail.username:}") String emailUsername,
        @Value("${spring.mail.password:}") String emailPassword
    ) {
        this.accounts = accounts;
        this.loginLimiter = loginLimiter;
        this.sessions = sessions;
        this.emailCodes = emailCodes;
        this.orders = orders;
        this.orderService = orderService;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.emailFrom = emailFrom;
        this.emailHost = emailHost;
        this.emailUsernameConfigured = !emailUsername.isBlank();
        this.emailPasswordConfigured = !emailPassword.isBlank();
    }

    @Transactional
    public AccountRegistrationResponse register(AccountRequests.Register request) {
        String email = request.email() == null || request.email().isBlank()
            ? null : normalizeEmail(request.email());
        String phone = request.phone() == null || request.phone().isBlank()
            ? null : normalizePhone(request.phone());
        if ((email == null) == (phone == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe somente um e-mail ou um telefone.");
        }
        if (email != null && !emailDeliveryConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O envio de e-mail ainda não está completo no Render. Confira: " + missingMailSettings() + ".");
        }
        validatePasswordBytes(request.password());
        if ((email != null && accounts.existsByEmailIgnoreCase(email))
            || (phone != null && accounts.existsByPhone(phone))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não foi possível criar a conta com esse identificador. Entre ou use outro e-mail/telefone.");
        }
        CustomerAccount account = accounts.saveAndFlush(new CustomerAccount(
            request.fullName(), email, phone, passwordEncoder.encode(request.password())));
        if (email != null) {
            issueEmailCode(account, CustomerAccountEmailCode.Purpose.VERIFY_EMAIL);
            return new AccountRegistrationResponse(
                "Enviamos um código de confirmação para seu e-mail.", null);
        }
        return new AccountRegistrationResponse("Conta criada com telefone. Sua sessão já está iniciada.",
            createSession(account));
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AccountAuthResponse verifyEmail(AccountRequests.EmailCode request) {
        CustomerAccount account = accounts.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .orElseThrow(() -> invalidCode());
        if (account.getEmailVerifiedAt() != null) throw invalidCode();
        CustomerAccountEmailCode code = emailCodes.findForUpdate(account.getId(),
                CustomerAccountEmailCode.Purpose.VERIFY_EMAIL)
            .orElseThrow(() -> invalidCode());
        verifyCode(code, request.code());
        account.verifyEmail();
        orders.attachUnclaimedOrdersByEmail(account.getEmail(), account.getId());
        emailCodes.delete(code);
        return createSession(account);
    }

    @Transactional
    public void resendVerificationEmail(AccountRequests.VerificationResend request) {
        accounts.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .filter(account -> account.getEmailVerifiedAt() == null)
            .ifPresent(account -> issueEmailCode(account, CustomerAccountEmailCode.Purpose.VERIFY_EMAIL));
    }

    @Transactional
    public AccountAuthResponse login(AccountRequests.Login request) {
        validatePasswordBytes(request.password());
        String submittedIdentifier = request.identifier().trim();
        String identifier = submittedIdentifier.contains("@")
            ? normalizeEmail(submittedIdentifier)
            : normalizePhone(submittedIdentifier);
        loginLimiter.check(identifier);
        var account = submittedIdentifier.contains("@")
            ? accounts.findByEmailIgnoreCase(identifier)
            : accounts.findByPhone(identifier);
        CustomerAccount customer = account.filter(CustomerAccount::canAuthenticate)
            .filter(value -> passwordEncoder.matches(request.password(), value.getPasswordHash()))
            .orElse(null);
        if (customer == null) {
            loginLimiter.failed(identifier);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail/telefone ou senha incorretos.");
        }
        loginLimiter.succeeded(identifier);
        return createSession(customer);
    }

    @Transactional
    public void requestPasswordReset(AccountRequests.ForgotPassword request) {
        CustomerAccount account = accounts.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .filter(value -> value.getEmailVerifiedAt() != null)
            .orElse(null);
        if (account != null) issueEmailCode(account, CustomerAccountEmailCode.Purpose.RESET_PASSWORD);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void resetPassword(AccountRequests.ResetPassword request) {
        validatePasswordBytes(request.newPassword());
        CustomerAccount account = accounts.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .filter(value -> value.getEmailVerifiedAt() != null)
            .orElseThrow(() -> invalidCode());
        CustomerAccountEmailCode code = emailCodes.findForUpdate(account.getId(),
                CustomerAccountEmailCode.Purpose.RESET_PASSWORD)
            .orElseThrow(() -> invalidCode());
        verifyCode(code, request.code());
        account.updatePassword(passwordEncoder.encode(request.newPassword()));
        sessions.deleteByAccountId(account.getId());
        emailCodes.delete(code);
    }

    @Transactional(readOnly = true)
    public CustomerAccountView getProfile(CustomerPrincipal principal) {
        return accounts.findById(principal.id()).map(CustomerAccountView::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sua sessão expirou. Entre novamente."));
    }

    @Transactional(readOnly = true)
    public java.util.List<CustomerOrderView> getOrders(CustomerPrincipal principal) {
        return orderService.listCustomerOrders(principal.id());
    }

    @Transactional
    public void logout(String rawToken) {
        sessions.deleteById(hashToken(rawToken));
    }

    @Transactional
    public void closeAccount(CustomerPrincipal principal, AccountRequests.CloseAccount request) {
        CustomerAccount account = accounts.findById(principal.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sua sessão expirou. Entre novamente."));
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A senha informada está incorreta.");
        }
        accounts.delete(account);
    }

    @Transactional(readOnly = true)
    public CustomerPrincipal authenticate(String rawToken) {
        if (rawToken == null || rawToken.length() > 100) return null;
        return sessions.findByTokenHashAndExpiresAtAfter(hashToken(rawToken), Instant.now())
            .map(CustomerAccountSession::getAccount)
            .filter(CustomerAccount::canAuthenticate)
            .map(account -> new CustomerPrincipal(account.getId(), account.getEmail(), account.getPhone(), account.getFullName()))
            .orElse(null);
    }

    private void issueEmailCode(CustomerAccount account, CustomerAccountEmailCode.Purpose purpose) {
        if (!emailDeliveryConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O envio de e-mail ainda não está completo no Render. Confira: " + missingMailSettings() + ".");
        }
        Instant now = Instant.now();
        CustomerAccountEmailCode code = emailCodes.findForUpdate(account.getId(), purpose).orElse(null);
        if (code != null && !code.canResend(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Aguarde um minuto antes de pedir outro código.");
        }
        String rawCode = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        Instant expiresAt = now.plus(EMAIL_CODE_LIFETIME);
        if (code == null) {
            code = new CustomerAccountEmailCode(account, purpose, passwordEncoder.encode(rawCode),
                now, expiresAt, now.plus(EMAIL_CODE_COOLDOWN));
        } else {
            code.replace(passwordEncoder.encode(rawCode), now, expiresAt, now.plus(EMAIL_CODE_COOLDOWN));
        }
        emailCodes.save(code);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailFrom);
        message.setTo(account.getEmail());
        message.setSubject(purpose == CustomerAccountEmailCode.Purpose.VERIFY_EMAIL
            ? "Confirme seu e-mail · FSI Studio"
            : "Código para redefinir sua senha · FSI Studio");
        message.setText("Seu código FSI Studio é " + rawCode + ". Ele expira em 10 minutos. "
            + "Se você não solicitou este código, ignore esta mensagem.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            Throwable cause = exception.getMostSpecificCause();
            LOGGER.warn("SMTP delivery failed for {} code ({})", purpose, cause.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível enviar o e-mail agora. Confira o host, a porta, o usuário SMTP, a senha de app e se o remetente está autorizado.", exception);
        }
    }

    private void verifyCode(CustomerAccountEmailCode code, String rawCode) {
        Instant now = Instant.now();
        if (code.isExpired(now) || code.isLocked()) {
            throw invalidCode();
        }
        if (!passwordEncoder.matches(rawCode, code.getCodeHash())) {
            code.incrementAttempts();
            throw invalidCode();
        }
    }

    private AccountAuthResponse createSession(CustomerAccount account) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
        sessions.save(new CustomerAccountSession(hashToken(rawToken), account, expiresAt));
        return new AccountAuthResponse(rawToken, expiresAt, CustomerAccountView.from(account));
    }

    private boolean emailDeliveryConfigured() {
        return !emailHost.isBlank() && !emailFrom.isBlank()
            && emailUsernameConfigured && emailPasswordConfigured;
    }

    private String missingMailSettings() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (emailHost.isBlank()) missing.add("MAIL_HOST");
        if (!emailUsernameConfigured) missing.add("MAIL_USERNAME");
        if (!emailPasswordConfigured) missing.add("MAIL_PASSWORD");
        if (emailFrom.isBlank()) missing.add("MAIL_FROM");
        return String.join(", ", missing);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhone(String rawPhone) {
        String trimmed = rawPhone.trim();
        String digits = trimmed.replaceAll("\\D", "");
        if (trimmed.startsWith("+")) return "+" + digits;
        if (digits.length() == 10 || digits.length() == 11) return "+55" + digits;
        if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith("55")) return "+" + digits;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Informe um telefone brasileiro com DDD ou um número internacional com código do país.");
    }

    private static ResponseStatusException invalidCode() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Código inválido ou expirado. Solicite um novo código e tente novamente.");
    }

    private static void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A senha pode ter no máximo 72 bytes em UTF-8.");
        }
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível nesta JVM.", exception);
        }
    }
}
