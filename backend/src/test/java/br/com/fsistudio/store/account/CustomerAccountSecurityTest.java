package br.com.fsistudio.store.account;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fsistudio.store.config.SecurityConfiguration;

@WebMvcTest(value = { CustomerAccountController.class, CustomerAccountAdminController.class }, properties = {
    "app.admin.username=admin",
    "app.admin.password=test-password",
    "app.cors.allowed-origins=http://localhost:5500"
})
@Import({ SecurityConfiguration.class, CustomerAccountSessionFilter.class })
class CustomerAccountSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerAccountService accounts;

    @Test
    void accountProfileRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/account/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void accountProfileDoesNotAcceptAdministratorBasicAuth() throws Exception {
        mockMvc.perform(get("/api/account/me").with(httpBasic("admin", "test-password")))
            .andExpect(status().isForbidden());
    }

    @Test
    void accountLoginRemainsPublic() throws Exception {
        when(accounts.login(org.mockito.ArgumentMatchers.any())).thenReturn(new AccountAuthResponse(
            "token", Instant.now().plusSeconds(60),
            new CustomerAccountView(UUID.randomUUID(), "Cliente", "cliente@example.com", "+5541999999999",
                Instant.now(), false)));
        mockMvc.perform(post("/api/account/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"identifier":"cliente@example.com","password":"uma-senha-segura"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void verificationResendRemainsPublic() throws Exception {
        mockMvc.perform(post("/api/account/verify-email/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"cliente@example.com"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void customerAccountSearchRequiresAdministratorAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/accounts").param("query", "cl"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCanSearchCustomerAccounts() throws Exception {
        when(accounts.searchAdminAccounts("cl")).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/accounts")
                .param("query", "cl")
                .with(httpBasic("admin", "test-password")))
            .andExpect(status().isOk());
    }

    @Test
    void administratorCanIssueTemporaryPasswordForCustomer() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accounts.issueTemporaryPassword(accountId)).thenReturn(
            new AdminTemporaryPasswordResponse("temporary-password", Instant.now().plusSeconds(3600)));
        mockMvc.perform(post("/api/admin/accounts/" + accountId + "/temporary-password")
                .with(httpBasic("admin", "test-password")))
            .andExpect(status().isOk());
    }

    @Test
    void temporaryPasswordSessionCanOnlyReadOwnProfileAndCompletePasswordReset() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(accounts.authenticate("temporary-token")).thenReturn(
            new CustomerPrincipal(accountId, null, "+5541999999999", "Cliente", true));
        when(accounts.getProfile(org.mockito.ArgumentMatchers.any())).thenReturn(
            new CustomerAccountView(accountId, "Cliente", null, "+5541999999999", Instant.now(), true));

        mockMvc.perform(get("/api/account/me")
                .header("Authorization", "Bearer temporary-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/account/orders")
                .header("Authorization", "Bearer temporary-token"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/account/complete-temporary-password-reset")
                .header("Authorization", "Bearer temporary-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"newPassword":"senha-nova-segura"}
                    """))
            .andExpect(status().isNoContent());
    }
}
