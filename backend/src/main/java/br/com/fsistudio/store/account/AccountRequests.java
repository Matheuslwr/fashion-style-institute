package br.com.fsistudio.store.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public final class AccountRequests {
    private AccountRequests() { }

    public record Register(
        @NotBlank @Size(max = 100) String fullName,
        @Email @Size(max = 120) String email,
        @Pattern(regexp = "[+0-9() .-]{10,24}") String phone,
        @NotBlank @Size(min = 10, max = 72) String password
    ) {
        @AssertTrue(message = "Informe somente um e-mail ou um telefone.")
        public boolean isContactChoiceValid() {
            boolean hasEmail = email != null && !email.isBlank();
            boolean hasPhone = phone != null && !phone.isBlank();
            return hasEmail != hasPhone;
        }
    }

    public record Login(
        @NotBlank @Size(max = 120) String identifier,
        @NotBlank @Size(max = 72) String password
    ) { }

    public record EmailCode(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code
    ) { }

    public record VerificationResend(@NotBlank @Email @Size(max = 120) String email) { }

    public record ForgotPassword(@NotBlank @Email @Size(max = 120) String email) { }

    public record ResetPassword(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotBlank @Size(min = 10, max = 72) String newPassword
    ) { }
}
