package br.com.fsistudio.store.order;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final String staticPayload;

    public PixPaymentService(@Value("${app.pix.static-payload:}") String staticPayload) {
        this.staticPayload = staticPayload.trim();
        if (!this.staticPayload.isBlank() && !hasValidCrc(this.staticPayload)) {
            throw new IllegalArgumentException("PIX_STATIC_PAYLOAD deve conter um código Pix com CRC válido");
        }
    }

    public boolean isConfigured() {
        return !staticPayload.isBlank();
    }

    public PixCode createCode() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O Pix da loja ainda não está configurado. Entre em contato com a loja.");
        }
        return new PixCode(staticPayload, createQrCode(staticPayload));
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

    private static boolean hasValidCrc(String payload) {
        if (payload.length() < 8 || payload.chars().anyMatch(character -> character < 0x20 || character > 0x7E)) {
            return false;
        }
        int crcStart = payload.length() - 4;
        int markerStart = crcStart - 4;
        if (!payload.startsWith("6304", markerStart) || !payload.substring(crcStart).matches("[0-9A-Fa-f]{4}")) {
            return false;
        }
        String expected = String.format(Locale.ROOT, "%04X",
            crc16(payload.substring(0, crcStart).getBytes(StandardCharsets.UTF_8)));
        return expected.equalsIgnoreCase(payload.substring(crcStart));
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
