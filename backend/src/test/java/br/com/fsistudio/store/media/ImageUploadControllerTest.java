package br.com.fsistudio.store.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

class ImageUploadControllerTest {
    @Test
    void acceptsSupportedImageSignatures() {
        assertTrue(ImageUploadController.matchesImageSignature("image/jpeg",
            new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
        assertTrue(ImageUploadController.matchesImageSignature("image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
        assertTrue(ImageUploadController.matchesImageSignature("image/webp",
            new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50}));
        assertTrue(ImageUploadController.matchesImageSignature("image/gif",
            new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}));
    }

    @Test
    void rejectsUnsupportedOrMismatchedImageSignatures() {
        byte[] text = "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(ImageUploadController.matchesImageSignature("image/png", text));
        assertFalse(ImageUploadController.matchesImageSignature("text/html", text));
        assertFalse(ImageUploadController.matchesImageSignature("image/jpeg", new byte[] {(byte) 0xff}));
    }

    @Test
    void reportsActionableCloudinaryAuthenticationFailures() {
        assertTrue(ImageUploadController.cloudinaryFailureMessage(new IOException("Server returned HTTP code 401"))
            .contains("CLOUDINARY_URL"));
    }

    @Test
    void reportsCloudinaryTimeoutsWithoutHidingTheCause() {
        assertTrue(ImageUploadController.cloudinaryFailureMessage(new IOException("upload failed",
            new SocketTimeoutException("read timed out"))).contains("demorou demais"));
    }
}
