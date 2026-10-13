package br.com.fsistudio.store.category;

import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class CategoryController {
    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categories.findAllByOrderByNameAsc().stream()
            .map(category -> new CategoryResponse(category.getId(), category.getName(), category.getSlug()))
            .toList();
    }

    @PostMapping("/admin/categories")
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        String slug = Normalizer.normalize(request.name(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        Category category = categories.save(new Category(request.name().trim(), slug));
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug());
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name) { }
    public record CategoryResponse(UUID id, String name, String slug) { }
}