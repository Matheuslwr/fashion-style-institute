package br.com.fsistudio.store.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
    @Valid Customer customer,
    @Valid Address address,
    @Size(min = 1, max = 20) List<@Valid Item> items
) {
    public record Customer(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Size(max = 24) String phone
    ) { }

    public record Address(
        @NotBlank @Pattern(regexp = "\\d{8}") String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String state,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 120) String street,
        @NotBlank @Size(max = 12) String number,
        @Size(max = 80) String complement,
        @NotBlank @Size(max = 80) String neighborhood
    ) { }

    public record Item(
        @NotBlank @Size(max = 160) String productSlug,
        @NotBlank @Size(max = 80) String color,
        @NotBlank @Size(max = 40) String size,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) int quantity
    ) { }
}
