package br.com.fsistudio.store.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
    private static final Duration TEMPORARY_PASSWORD_LIFETIME = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_SESSION_LIFETIME = Duration.ofMinutes(10);
    private static final String TEMPORARY_PASSWORD_CHARACTERS =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final Pattern BREVO_ERROR_CODE_PATTERN =
        Pattern.compile("\"code\"\\s*:\\s*\"([A-Za-z0-9_-]{1,60})\"");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final CustomerAccountRepository accounts;
    private final CustomerLoginAttemptLimiter loginLimiter;
    private final CustomerAccountSessionRepository sessions;
    private final CustomerAccountEmailCodeRepository emailCodes;
    private final StoreOrderRepository orders;
    private final StoreOrderService orderService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final RestClient brevoClient;
    private final String emailFrom;
    private final String brevoApiKey;
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
        RestClient.Builder restClientBuilder,
        @Value("${app.mail.from:}") String emailFrom,
        @Value("${app.mail.brevo-api-key:}") String brevoApiKey,
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
        this.brevoClient = restClientBuilder.baseUrl("https://api.brevo.com/v3").build();
        this.emailFrom = emailFrom;
        this.brevoApiKey = brevoApiKey;
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
            .filter(value -> !value.isTemporaryPasswordExpired(Instant.now()))
            .filter(value -> passwordEncoder.matches(request.password(), value.getPasswordHash()))
            .orElse(null);
        if (customer == null) {
            loginLimiter.failed(identifier);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail/telefone ou senha incorretos.");
        }
        loginLimiter.succeeded(identifier);
        return createSession(customer);
    }

    @Transactional(readOnly = true)
    public java.util.List<AdminCustomerAccountView> searchAdminAccounts(String query) {
        String term = query.trim();
        String phoneTerm = term.matches("[+0-9() .-]+")
            ? term.replaceAll("\\D", "")
            : term;
        if (phoneTerm.isBlank()) phoneTerm = term;
        return accounts
            .findTop20ByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingOrderByCreatedAtDesc(
                term, term, phoneTerm)
            .stream()
            .map(AdminCustomerAccountView::from)
            .toList();
    }

    @Transactional
    public AdminTemporaryPasswordResponse issueTemporaryPassword(UUID accountId) {
        CustomerAccount account = accounts.findByIdForUpdate(accountId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
        if (!account.canAuthenticate()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta conta ainda precisa confirmar o e-mail antes de entrar.");
        }
        String temporaryPassword = generateTemporaryPassword();
        Instant expiresAt = Instant.now().plus(TEMPORARY_PASSWORD_LIFETIME);
        account.setTemporaryPassword(passwordEncoder.encode(temporaryPassword), expiresAt);
        sessions.deleteByAccountId(accountId);
        emailCodes.findForUpdate(accountId, CustomerAccountEmailCode.Purpose.RESET_PASSWORD)
            .ifPresent(emailCodes::delete);
        return new AdminTemporaryPasswordResponse(temporaryPassword, expiresAt);
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

    @Transactional
    public void completeTemporaryPasswordReset(
        CustomerPrincipal principal,
        AccountRequests.CompleteTemporaryPasswordReset request
    ) {
        validatePasswordBytes(request.newPassword());
        CustomerAccount account = accounts.findByIdForUpdate(principal.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Sua sessão expirou. Entre novamente."));
        if (!account.isPasswordResetRequired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta conta não precisa trocar a senha temporária.");
        }
        if (account.isTemporaryPasswordExpired(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                "A senha temporária expirou. Peça ao atendimento para emitir outra.");
        }
        account.updatePassword(passwordEncoder.encode(request.newPassword()));
        sessions.deleteByAccountId(account.getId());
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
            .map(account -> new CustomerPrincipal(account.getId(), account.getEmail(), account.getPhone(),
                account.getFullName(), account.isPasswordResetRequired()))
            .orElse(null);
    }

    private void issueEmailCode(CustomerAccount account, CustomerAccountEmailCode.Purpose purpose) {
        if (!emailDeliveryConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O envio de e-mail ainda não está configurado. Configure BREVO_API_KEY e MAIL_FROM para usar a API HTTPS, ou confira: "
                    + missingMailSettings() + ".");
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
        if (!brevoApiKey.isBlank()) {
            sendWithBrevo(account.getEmail(), message.getSubject(), message.getText(), purpose);
            return;
        }
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            Throwable cause = exception.getMostSpecificCause();
            LOGGER.warn("SMTP delivery failed for {} code ({})", purpose, cause.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O envio por SMTP falhou. No Render Free, configure BREVO_API_KEY e MAIL_FROM para enviar pela API HTTPS.", exception);
        }
    }

    private void sendWithBrevo(String recipient, String subject, String text, CustomerAccountEmailCode.Purpose purpose) {
        java.util.Map<String, Object> payload = java.util.Map.of(
            "sender", java.util.Map.of("name", "FSI Studio", "email", emailFrom),
            "to", java.util.List.of(java.util.Map.of("email", recipient)),
            "subject", subject,
            "textContent", text
        );
        try {
            brevoClient.post()
                .uri("/smtp/email")
                .header("api-key", brevoApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            String errorCode = brevoErrorCode(exception.getResponseBodyAsString());
            LOGGER.warn("Brevo delivery failed for {} code (HTTP {}, provider code {})",
                purpose, exception.getStatusCode().value(), errorCode);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                brevoFailureMessage(exception.getStatusCode().value(), errorCode));
        } catch (RestClientException exception) {
            LOGGER.warn("Brevo delivery failed for {} code ({})", purpose, exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível conectar ao serviço de e-mail pela API HTTPS. Tente novamente mais tarde.", exception);
        }
    }

    private static String brevoErrorCode(String responseBody) {
        Matcher matcher = BREVO_ERROR_CODE_PATTERN.matcher(responseBody);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "unknown";
    }

    private static String brevoFailureMessage(int status, String errorCode) {
        return switch (errorCode) {
            case "unauthorized" -> "O Brevo não aceitou BREVO_API_KEY. Confira se a chave é válida e tem permissão para e-mails transacionais.";
            case "permission_denied" -> "A chave do Brevo não tem permissão para enviar e-mails transacionais. Habilite essa permissão na chave API.";
            case "account_under_validation" -> "O Brevo ainda está validando a conta. Conclua a validação solicitada no painel do Brevo.";
            case "not_enough_credits", "insufficient_credits" -> "A conta do Brevo não tem créditos disponíveis para enviar o e-mail.";
            case "invalid_parameter", "missing_parameter", "bad_request", "unprocessable_entity", "sender_not_found" ->
                "O Brevo recusou o remetente. Confira se MAIL_FROM é exatamente o remetente verificado no Brevo.";
            default -> "O Brevo recusou o envio (HTTP " + status + ", código " + errorCode
                + "). Confira a chave API, a permissão de envio transacional, o remetente verificado e o status da conta no Brevo.";
        };
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
        Instant expiresAt = Instant.now().plus(account.isPasswordResetRequired()
            ? PASSWORD_RESET_SESSION_LIFETIME : SESSION_LIFETIME);
        sessions.save(new CustomerAccountSession(hashToken(rawToken), account, expiresAt));
        return new AccountAuthResponse(rawToken, expiresAt, CustomerAccountView.from(account));
    }

    private static String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(18);
        for (int index = 0; index < 18; index++) {
            password.append(TEMPORARY_PASSWORD_CHARACTERS.charAt(
                RANDOM.nextInt(TEMPORARY_PASSWORD_CHARACTERS.length())));
        }
        return password.toString();
    }

    private boolean emailDeliveryConfigured() {
        boolean smtpConfigured = !emailHost.isBlank() && !emailFrom.isBlank()
            && emailUsernameConfigured && emailPasswordConfigured;
        return !emailFrom.isBlank() && (!brevoApiKey.isBlank() || smtpConfigured);
    }

    private String missingMailSettings() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (emailFrom.isBlank()) missing.add("MAIL_FROM");
        if (brevoApiKey.isBlank()) {
            missing.add("BREVO_API_KEY (ou MAIL_HOST, MAIL_USERNAME e MAIL_PASSWORD para SMTP)");
        }
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
