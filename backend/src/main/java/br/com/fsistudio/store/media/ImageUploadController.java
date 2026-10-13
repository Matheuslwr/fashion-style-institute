package br.com.fsistudio.store.media;

import java.io.IOException;
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