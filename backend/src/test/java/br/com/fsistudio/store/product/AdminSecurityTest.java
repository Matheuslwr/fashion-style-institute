package br.com.fsistudio.store.product;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fsistudio.store.config.SecurityConfiguration;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@WebMvcTest(value = ProductController.class, properties = {
    "app.admin.username=admin",
    "app.admin.password=test-password",
    "app.cors.allowed-origins=http://localhost:5500"
})
@Import(SecurityConfiguration.class)
class AdminSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService products;

    @Test
    void adminApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiAcceptsConfiguredAdministrator() throws Exception {
        when(products.listAdmin()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/products").with(httpBasic("admin", "test-password")))
            .andExpect(status().isOk());
    }

    @Test
    void publicCatalogDoesNotRequireAuthentication() throws Exception {
        when(products.listPublic(null, null, null)).thenReturn(List.of());
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk());
    }

    @Test
    void checkoutCorsAllowsIdempotencyHeader() throws Exception {
        mockMvc.perform(options("/api/orders")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,idempotency-key"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Headers",
                org.hamcrest.Matchers.containsString("idempotency-key")));
    }
}