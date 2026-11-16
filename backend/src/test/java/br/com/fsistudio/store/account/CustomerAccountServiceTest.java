package br.com.fsistudio.store.account;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
            orderService, new BCryptPasswordEncoder(), mailSender, RestClient.builder(), "", "", "", "", "");
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
    void adminTemporaryPasswordIsHashedAndInvalidatesExistingSessions() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-com-dez"));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        when(emailCodes.findForUpdate(account.getId(), CustomerAccountEmailCode.Purpose.RESET_PASSWORD))
            .thenReturn(Optional.empty());

        AdminTemporaryPasswordResponse response = service.issueTemporaryPassword(account.getId());

        assertEquals(18, response.temporaryPassword().length());
        assertTrue(encoder.matches(response.temporaryPassword(), account.getPasswordHash()));
        assertTrue(account.isPasswordResetRequired());
        assertTrue(response.expiresAt().isAfter(java.time.Instant.now()));
        verify(sessions).deleteByAccountId(account.getId());
    }

    @Test
    void temporaryPasswordLoginRequiresPasswordChange() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-com-dez"));
        account.setTemporaryPassword(encoder.encode("SenhaTemp123abc456"), java.time.Instant.now().plusSeconds(3600));
        when(accounts.findByPhone("+5541999999999")).thenReturn(Optional.of(account));

        AccountAuthResponse response = service.login(new AccountRequests.Login(
            "(41) 99999-9999", "SenhaTemp123abc456"));

        assertTrue(response.account().passwordResetRequired());
    }

    @Test
    void temporaryPasswordMustBeChangedBeforeTheAccountCanContinue() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-temporaria"));
        account.setTemporaryPassword(encoder.encode("senha-temporaria"), java.time.Instant.now().plusSeconds(3600));
        when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        CustomerPrincipal principal = new CustomerPrincipal(
            account.getId(), null, account.getPhone(), account.getFullName(), true);

        service.completeTemporaryPasswordReset(principal,
            new AccountRequests.CompleteTemporaryPasswordReset("senha-nova-segura"));

        org.junit.jupiter.api.Assertions.assertFalse(account.isPasswordResetRequired());
        org.junit.jupiter.api.Assertions.assertTrue(encoder.matches("senha-nova-segura", account.getPasswordHash()));
        verify(sessions).deleteByAccountId(account.getId());
    }

    @Test
    void expiredTemporaryPasswordCannotBeUsedToSignIn() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-temporaria"));
        account.setTemporaryPassword(encoder.encode("senha-temporaria"), java.time.Instant.now().minusSeconds(1));
        when(accounts.findByPhone("+5541999999999")).thenReturn(Optional.of(account));

        assertThrows(ResponseStatusException.class, () -> service.login(
            new AccountRequests.Login("(41) 99999-9999", "senha-temporaria")));
    }

    @Test
    void emailRegistrationReportsWhenNoEmailProviderIsConfigured() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.register(new AccountRequests.Register(
                "Cliente", "cliente@example.com", null, "senha-com-dez")));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("MAIL_FROM"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("BREVO_API_KEY"));
        verifyNoInteractions(accounts);
    }

    @Test
    void emailRegistrationUsesBrevoHttpsApiWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("api-key", "test-brevo-key"))
            .andExpect(content().json("""
                {
                  "sender":{"name":"FSI Studio","email":"loja@example.com"},
                  "to":[{"email":"cliente@example.com"}],
                  "subject":"Confirme seu e-mail · FSI Studio"
                }
                """))
            .andRespond(withSuccess());

        CustomerAccountService apiService = new CustomerAccountService(
            accounts, loginLimiter, sessions, emailCodes, orders, orderService,
            new BCryptPasswordEncoder(), mailSender, builder, "loja@example.com",
            "test-brevo-key", "", "", "");
        when(accounts.saveAndFlush(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailCodes.findForUpdate(any(), any())).thenReturn(Optional.empty());

        AccountRegistrationResponse response = apiService.register(new AccountRequests.Register(
            "Cliente", "cliente@example.com", null, "senha-com-dez"));

        assertNull(response.session());
        server.verify();
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void emailRegistrationExplainsWhenBrevoRejectsTheSender() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withBadRequest().body("""
                {"code":"invalid_parameter","message":"example provider detail"}
                """));

        CustomerAccountService apiService = new CustomerAccountService(
            accounts, loginLimiter, sessions, emailCodes, orders, orderService,
            new BCryptPasswordEncoder(), mailSender, builder, "loja@example.com",
            "test-brevo-key", "", "", "");
        when(accounts.saveAndFlush(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailCodes.findForUpdate(any(), any())).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> apiService.register(new AccountRequests.Register(
                "Cliente", "cliente@example.com", null, "senha-com-dez")));

        assertTrue(exception.getReason().contains("remetente verificado"));
        org.junit.jupiter.api.Assertions.assertFalse(exception.getReason().contains("example provider detail"));
        server.verify();
    }

    @Test
    void closingAccountRequiresCorrectPasswordAndDeletesOnlyTheAccount() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        CustomerAccount account = new CustomerAccount("Cliente", null, "+5541999999999",
            encoder.encode("senha-com-dez"));
        UUID accountId = account.getId();
        CustomerPrincipal principal = new CustomerPrincipal(accountId, null, account.getPhone(), account.getFullName(), false);
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
        CustomerPrincipal principal = new CustomerPrincipal(accountId, null, account.getPhone(), account.getFullName(), false);
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(ResponseStatusException.class,
            () -> service.closeAccount(principal, new AccountRequests.CloseAccount("senha-errada")));

        verify(accounts, never()).delete(any(CustomerAccount.class));
    }

    @Test
    void emailRegistrationReturnsSafeGuidanceWhenSmtpRejectsDelivery() {
        CustomerAccountService configuredService = new CustomerAccountService(
            accounts, loginLimiter, sessions, emailCodes, orders, orderService,
            new BCryptPasswordEncoder(), mailSender, RestClient.builder(),
            "loja@example.com", "", "smtp.gmail.com", "loja@example.com", "test-only-app-password");
        when(accounts.saveAndFlush(any(CustomerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailCodes.findForUpdate(any(), any())).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new MailSendException("test SMTP failure"))
            .when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> configuredService.register(new AccountRequests.Register(
                "Cliente", "cliente@example.com", null, "senha-com-dez")));

        assertEquals(503, exception.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(exception.getReason().contains("BREVO_API_KEY"));
        org.junit.jupiter.api.Assertions.assertFalse(exception.getReason().contains("test-only-app-password"));
    }
}
