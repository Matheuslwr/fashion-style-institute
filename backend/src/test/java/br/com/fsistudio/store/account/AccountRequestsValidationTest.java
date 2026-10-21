package br.com.fsistudio.store.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class AccountRequestsValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void registrationRequiresExactlyOneContact() {
        assertEquals(1, validator.validate(new AccountRequests.Register(
            "Cliente", null, null, "senha-com-dez")).size());
        assertEquals(1, validator.validate(new AccountRequests.Register(
            "Cliente", "cliente@example.com", "+5541999999999", "senha-com-dez")).size());
        assertTrue(validator.validate(new AccountRequests.Register(
            "Cliente", "cliente@example.com", null, "senha-com-dez")).isEmpty());
        assertTrue(validator.validate(new AccountRequests.Register(
            "Cliente", null, "+5541999999999", "senha-com-dez")).isEmpty());
    }

    @Test
    void registrationPasswordMustHaveAtLeastTenCharacters() {
        assertEquals(1, validator.validate(new AccountRequests.Register(
            "Cliente", null, "+5541999999999", "123456789")).size());
        assertTrue(validator.validate(new AccountRequests.Register(
            "Cliente", null, "+5541999999999", "1234567890")).isEmpty());
    }
}
