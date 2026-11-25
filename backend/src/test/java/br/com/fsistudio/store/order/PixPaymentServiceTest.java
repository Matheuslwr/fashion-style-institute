package br.com.fsistudio.store.order;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

class PixPaymentServiceTest {
    @Test
    void preservesTheBankGeneratedPixPayloadInTheReadablePngQrCode() throws Exception {
        String bankPayload = staticPayload();
        PixPaymentService service = new PixPaymentService(bankPayload);

        PixPaymentService.PixCode code = service.createCode();

        assertEquals(bankPayload, code.payload());
        byte[] image = Base64.getDecoder().decode(code.qrCodeBase64());
        assertEquals((byte) 0x89, image[0]);
        assertEquals((byte) 'P', image[1]);
        assertEquals(code.payload(), new MultiFormatReader().decode(new BinaryBitmap(
            new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(
                new java.io.ByteArrayInputStream(image)))))).getText());
    }

    @Test
    void reportsPixConfigurationAsMissingWhenNoBankPayloadIsProvided() {
        PixPaymentService service = new PixPaymentService("");

        assertFalse(service.isConfigured());
        assertDoesNotThrow(() -> new PixPaymentService(""));
    }

    @Test
    void rejectsBankPayloadWithInvalidCrc() {
        assertThrows(IllegalArgumentException.class, () -> new PixPaymentService("00020163040000"));
    }

    private static String staticPayload() {
        String merchantAccount = field("00", "BR.GOV.BCB.PIX") + field("01", "test-pix-key");
        String body = field("00", "01")
            + field("26", merchantAccount)
            + field("52", "0000")
            + field("53", "986")
            + field("58", "BR")
            + field("59", "LOJA EXEMPLO")
            + field("60", "CURITIBA")
            + field("62", field("05", "TX123"))
            + "6304";
        return body + crc(body + "0000");
    }

    private static String field(String id, String value) {
        return id + String.format(java.util.Locale.ROOT, "%02d", value.length()) + value;
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
