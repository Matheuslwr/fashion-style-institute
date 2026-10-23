package br.com.fsistudio.store.account;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.order.StoreOrderRepository;
import br.com.fsistudio.store.order.StoreOrderService;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceTest {
    @Mock
    private CustomerAccountRepository accounts;
    @Mock
    private CustomerLoginAttemptLimiter loginLimiter;
    @Mock
    private CustomerAccountSessionRepository sessions;
    @Mock
    private CustomerAccountEmailCodeRepository emailCodes;
    @Mock
    private StoreOrderRepository orders;
    @Mock
    private StoreOrderService orderService;
    @Mock
    private JavaMailSender mailSender;

    private CustomerAccountService service;

    @BeforeEach
    void setUp() {
        service = new CustomerAccountService(accounts, loginLimiter, sessions, emailCodes, orders,
            orderService, new BCryptPasswordEncoder(), mailSender, "", "", "", "");
    }

    @Test
    void verifiedEmailCannotCreateSessionWithoutAValidCode() {
        CustomerAccount account = new CustomerAccount("Cliente", "cliente@example.com",
            "+5541999999999", "password-hash");
        account.verifyEmail();
        when(accounts.findByEmailIgnoreCase("cliente@example.com")).thenReturn(Optional.of(account));

        assertThrows(ResponseStatusException.class,
            () -> service.verifyEmail(new AccountRequests.EmailCode("cliente@example.com", "000000")));

        verify(emailCodes, never()).findForUpdate(any(), any());
        verify(sessions, never()).save(any());
    }

    @Test
    void phoneRegistrationCreatesAnAccountAndStartsASessionWithoutEmail() {
        when(accounts.saveAndFlush(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AccountRegistrationResponse response = service.register(new AccountRequests.Register(
            "Cliente", null, "(41) 99999-9999", "senha-com-dez"));

        assertNotNull(response.session());
        assertNull(response.session().account().email());
        assertEquals("+5541999999999", response.session().account().phone());
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void phoneOnlyAccountCanLoginWithoutEmailVerification() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-com-dez"));
        when(accounts.findByPhone("+5541999999999")).thenReturn(Optional.of(account));

        AccountAuthResponse response = service.login(new AccountRequests.Login(
            "(41) 99999-9999", "senha-com-dez"));

        assertNotNull(response.token());
        assertNull(response.account().email());
    }

    @Test
    void emailRegistrationReportsWhichSmtpSettingsAreMissing() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.register(new AccountRequests.Register(
                "Cliente", "cliente@example.com", null, "senha-com-dez")));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("MAIL_HOST"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("MAIL_FROM"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("MAIL_USERNAME"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("MAIL_PASSWORD"));
        verifyNoInteractions(accounts);
    }

    @Test
    void closingAccountRequiresCorrectPasswordAndDeletesOnlyTheAccount() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-com-dez"));
        UUID accountId = account.getId();
        CustomerPrincipal principal = new CustomerPrincipal(accountId, null, account.getPhone(), account.getFullName());
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        service.closeAccount(principal, new AccountRequests.CloseAccount("senha-com-dez"));

        verify(accounts).delete(account);
    }

    @Test
    void closingAccountRejectsAnIncorrectPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-com-dez"));
        UUID accountId = account.getId();
        CustomerPrincipal principal = new CustomerPrincipal(accountId, null, account.getPhone(), account.getFullName());
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(ResponseStatusException.class,
            () -> service.closeAccount(principal, new AccountRequests.CloseAccount("senha-errada")));

        verify(accounts, never()).delete(any(CustomerAccount.class));
    }

    @Test
    void emailRegistrationReturnsSafeGuidanceWhenSmtpRejectsDelivery() {
        CustomerAccountService configuredService = new CustomerAccountService(
            accounts, loginLimiter, sessions, emailCodes, orders, orderService,
            new BCryptPasswordEncoder(), mailSender, "loja@example.com", "smtp.gmail.com",
            "loja@example.com", "test-only-app-password");
        when(accounts.saveAndFlush(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailCodes.findForUpdate(any(), any())).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new MailSendException("test SMTP failure"))
            .when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> configuredService.register(new AccountRequests.Register(
                "Cliente", "cliente@example.com", null, "senha-com-dez")));

        assertEquals(503, exception.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("senha de app"));
        org.junit.jupiter.api.Assertions.assertFalse(exception.getReason().contains("test-only-app-password"));
    }
}
