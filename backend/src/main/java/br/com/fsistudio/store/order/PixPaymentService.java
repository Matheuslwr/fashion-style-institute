package br.com.fsistudio.store.order;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;

@Service
public class PixPaymentService {
    private final String key;
    private final String receiverName;
    private final String receiverCity;

    public PixPaymentService(
        @Value("${app.pix.key:}") String key,
        @Value("${app.pix.receiver-name:}") String receiverName,
        @Value("${app.pix.receiver-city:}") String receiverCity
    ) {
        this.key = key.trim();
        this.receiverName = normalizeMerchantField(receiverName, 25);
        this.receiverCity = normalizeMerchantField(receiverCity, 15);
        if (!this.key.isBlank() && this.key.getBytes(StandardCharsets.UTF_8).length > 77) {
            throw new IllegalArgumentException("PIX_KEY excede o tamanho permitido");
        }
        if (this.key.chars().anyMatch(character -> character < 0x20 || character > 0x7E)) {
            throw new IllegalArgumentException("PIX_KEY deve conter somente caracteres imprimíveis");
        }
        if ((!this.receiverName.isBlank() || !this.receiverCity.isBlank())
            && (this.receiverName.isBlank() || this.receiverCity.isBlank())) {
            throw new IllegalArgumentException("PIX_RECEIVER_NAME e PIX_RECEIVER_CITY devem ser preenchidos juntos");
        }
    }

    public boolean isConfigured() {
        return !key.isBlank() && !receiverName.isBlank() && !receiverCity.isBlank();
    }

    public PixCode createCode(StoreOrder order) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O Pix da loja ainda não está configurado. Entre em contato com a loja.");
        }
        String merchantAccount = field("00", "BR.GOV.BCB.PIX") + field("01", key);
        String additionalData = field("05", order.getOrderNumber());
        String payload = field("00", "01")
            + field("01", "12")
            + field("26", merchantAccount)
            + field("52", "0000")
            + field("53", "986")
            + field("54", BigDecimal.valueOf(order.getTotalCents(), 2).toPlainString())
            + field("58", "BR")
            + field("59", receiverName)
            + field("60", receiverCity)
            + field("62", additionalData)
            + "6304";
        payload += String.format(Locale.ROOT, "%04X", crc16(payload.getBytes(StandardCharsets.UTF_8)));
        return new PixCode(payload, createQrCode(payload));
    }

    private static String createQrCode(String payload) {
        try {
            var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 360, 360);
            ByteArrayOutputStream image = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", image);
            return Base64.getEncoder().encodeToString(image.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o QR Code Pix", exception);
        }
    }

    private static String field(String id, String value) {
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > 99) throw new IllegalArgumentException("Campo Pix excede o tamanho permitido");
        return id + String.format(Locale.ROOT, "%02d", length) + value;
    }

    private static String normalizeMerchantField(String value, int maxLength) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9 ]", " ")
            .trim()
            .replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }

    private static int crc16(byte[] bytes) {
        int crc = 0xFFFF;
        for (byte value : bytes) {
            crc ^= (value & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return crc;
    }

    public record PixCode(String payload, String qrCodeBase64) { }
}
