package br.com.fsistudio.store.account;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fsistudio.store.config.SecurityConfiguration;

@WebMvcTest(value = CustomerAccountController.class, properties = {
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
            new CustomerAccountView(UUID.randomUUID(), "Cliente", "cliente@example.com", "+5541999999999", Instant.now())));
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
}
