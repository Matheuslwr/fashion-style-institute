package br.com.fsistudio.store.account;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/admin/accounts")
public class CustomerAccountAdminController {
    private final CustomerAccountService accounts;

    public CustomerAccountAdminController(CustomerAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<AdminCustomerAccountView> search(
        @RequestParam("query") @NotBlank @Size(min = 2, max = 120) String query
    ) {
        return accounts.searchAdminAccounts(query);
    }

    @PostMapping("/{accountId}/temporary-password")
    public AdminTemporaryPasswordResponse issueTemporaryPassword(@PathVariable UUID accountId) {
        return accounts.issueTemporaryPassword(accountId);
    }
}
