package br.com.fsistudio.store.order;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

class PixPaymentServiceTest {
    @Test
    void createsAmountSpecificPixPayloadAndReadablePngQrCode() throws Exception {
        PixPaymentService service = new PixPaymentService("test-pix-key", "Loja Exemplo", "Curitiba");
        CheckoutRequest.Customer customer = new CheckoutRequest.Customer("Cliente", "cliente@example.com", "41999999999");
        StoreOrder order = new StoreOrder(UUID.randomUUID(), UUID.randomUUID(), "FSI-A1B2C3D4", customer,
            "80000000", "PR", "Curitiba", "Rua", "10", null, "Centro", 0, 1234, 500,
            List.of());

        PixPaymentService.PixCode code = service.createCode(order);

        assertTrue(code.payload().contains("BR.GOV.BCB.PIX"));
        assertTrue(code.payload().contains("test-pix-key"));
        assertTrue(code.payload().contains("17.34"));
        assertTrue(code.payload().contains("LOJA EXEMPLO"));
        assertTrue(code.payload().contains("CURITIBA"));
        assertTrue(code.payload().endsWith(crc(code.payload())));
        byte[] image = Base64.getDecoder().decode(code.qrCodeBase64());
        assertEquals((byte) 0x89, image[0]);
        assertEquals((byte) 'P', image[1]);
        assertEquals(code.payload(), new MultiFormatReader().decode(new BinaryBitmap(
            new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(
                new java.io.ByteArrayInputStream(image)))))).getText());
    }

    @Test
    void reportsPixConfigurationAsMissingWithoutExposingOrInventingAKey() {
        PixPaymentService service = new PixPaymentService("", "", "");

        assertFalse(service.isConfigured());
        assertDoesNotThrow(() -> new PixPaymentService("", "", ""));
    }

    private static String crc(String payloadWithCrcPrefix) {
        String input = payloadWithCrcPrefix.substring(0, payloadWithCrcPrefix.length() - 4);
        int value = 0xFFFF;
        for (byte current : input.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            value ^= (current & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                value = (value & 0x8000) != 0 ? (value << 1) ^ 0x1021 : value << 1;
                value &= 0xFFFF;
            }
        }
        return String.format(java.util.Locale.ROOT, "%04X", value);
    }
}
