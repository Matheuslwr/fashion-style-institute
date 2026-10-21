package br.com.fsistudio.store.account;

import java.util.Map;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.order.CustomerOrderView;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/account")
public class CustomerAccountController {
    private final CustomerAccountService accounts;

    public CustomerAccountController(CustomerAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    public AccountRegistrationResponse register(@Valid @RequestBody AccountRequests.Register request) {
        return accounts.register(request);
    }

    @PostMapping("/verify-email")
    public AccountAuthResponse verifyEmail(@Valid @RequestBody AccountRequests.EmailCode request) {
        return accounts.verifyEmail(request);
    }

    @PostMapping("/verify-email/resend")
    public Map<String, String> resendVerificationEmail(
        @Valid @RequestBody AccountRequests.VerificationResend request
    ) {
        accounts.resendVerificationEmail(request);
        return Map.of("message", "Se a conta estiver aguardando confirmação, enviaremos um novo código.");
    }

    @PostMapping("/login")
    public AccountAuthResponse login(@Valid @RequestBody AccountRequests.Login request) {
        return accounts.login(request);
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody AccountRequests.ForgotPassword request) {
        accounts.requestPasswordReset(request);
        return Map.of("message", "Se existe uma conta confirmada com esse e-mail, enviaremos um código de recuperação.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody AccountRequests.ResetPassword request) {
        accounts.resetPassword(request);
        return Map.of("message", "Senha atualizada. Entre com sua nova senha.");
    }

    @GetMapping("/me")
    public CustomerAccountView profile(@AuthenticationPrincipal CustomerPrincipal principal) {
        return accounts.getProfile(requirePrincipal(principal));
    }

    @GetMapping("/orders")
    public List<CustomerOrderView> orders(@AuthenticationPrincipal CustomerPrincipal principal) {
        return accounts.getOrders(requirePrincipal(principal));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sua sessão expirou. Entre novamente.");
        }
        accounts.logout(authorization.substring("Bearer ".length()));
        return Map.of("message", "Você saiu da sua conta.");
    }

    @DeleteMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeAccount(
        @AuthenticationPrincipal CustomerPrincipal principal,
        @Valid @RequestBody AccountRequests.CloseAccount request
    ) {
        accounts.closeAccount(requirePrincipal(principal), request);
    }

    private static CustomerPrincipal requirePrincipal(CustomerPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sua sessão expirou. Entre novamente.");
        }
        return principal;
    }
}
