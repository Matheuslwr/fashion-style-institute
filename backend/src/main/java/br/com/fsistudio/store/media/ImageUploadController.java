package br.com.fsistudio.store.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/images")
public class ImageUploadController {
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
        "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif");
    private final Path uploadDirectory;
    private final String storageProvider;
    private final String cloudinaryUrl;

    public ImageUploadController(
        @Value("${app.upload-dir:uploads}") String uploadDirectory,
        @Value("${app.image-storage:local}") String storageProvider,
        @Value("${CLOUDINARY_URL:}") String cloudinaryUrl
    ) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.storageProvider = storageProvider;
        this.cloudinaryUrl = cloudinaryUrl;
    }

    @PostMapping
    public ImageResponse upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 8 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem deve ter até 8 MB");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : IMAGE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use uma imagem JPEG, PNG, WebP ou GIF");
        }
        try (InputStream input = file.getInputStream()) {
            if (!matchesImageSignature(contentType, input.readNBytes(12))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O conteúdo do arquivo não corresponde a uma imagem válida");
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível ler a imagem", exception);
        }
        if ("cloudinary".equalsIgnoreCase(storageProvider)) {
            return uploadToCloudinary(file);
        }

        String filename = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory);
            file.transferTo(uploadDirectory.resolve(filename));
            return new ImageResponse("/uploads/" + filename);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar a imagem", exception);
        }
    }

    static boolean matchesImageSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> header.length >= 3
                && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            case "image/png" -> header.length >= 8
                && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47
                && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a;
            case "image/webp" -> header.length >= 12
                && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
            case "image/gif" -> header.length >= 6
                && header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46
                && header[3] == 0x38 && (header[4] == 0x37 || header[4] == 0x39) && header[5] == 0x61;
            default -> false;
        };
    }

    private ImageResponse uploadToCloudinary(MultipartFile file) {
        if (cloudinaryUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O armazenamento de imagens não está configurado");
        }
        try {
            Map<?, ?> result = new Cloudinary(cloudinaryUrl).uploader().upload(file.getBytes(),
                ObjectUtils.asMap("folder", "fsi-studio/products", "resource_type", "image"));
            Object secureUrl = result.get("secure_url");
            if (!(secureUrl instanceof String imageUrl) || imageUrl.isBlank()) {
                throw new IOException("O provedor não retornou a URL da imagem");
            }
            return new ImageResponse(imageUrl);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível enviar a imagem ao Cloudinary", exception);
        }
    }

    public record ImageResponse(String imageUrl) { }
}