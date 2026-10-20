package br.com.fsistudio.store.order;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import br.com.fsistudio.store.config.SecurityConfiguration;
import br.com.fsistudio.store.account.CustomerAccountRepository;
import br.com.fsistudio.store.account.CustomerAccountSessionFilter;
import br.com.fsistudio.store.account.CustomerAccountService;

@WebMvcTest(value = OrderController.class, properties = {
    "app.admin.username=admin",
    "app.admin.password=test-password",
    "app.cors.allowed-origins=http://localhost:5500"
})
@Import({ SecurityConfiguration.class, CustomerAccountSessionFilter.class })
class OrderAdminSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StoreOrderService orders;

    @MockBean
    private StoreOrderRepository repository;

    @MockBean
    private PixPaymentService pix;

    @MockBean
    private CustomerAccountRepository accounts;

    @MockBean
    private CustomerAccountService customerAccounts;

    @Test
    void adminOrderListRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOrderListAcceptsConfiguredAdministrator() throws Exception {
        when(orders.listRecentOrders()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/orders").with(httpBasic("admin", "test-password")))
            .andExpect(status().isOk());
    }
}
