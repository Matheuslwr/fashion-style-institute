package br.com.fsistudio.store.product;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping("/products")
    public List<ProductResponse> products(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Boolean featured,
        @RequestParam(required = false) String q
    ) {
        return products.listPublic(category, featured, q);
    }

    @GetMapping("/products/{slug}")
    public ProductResponse product(@PathVariable String slug) {
        return products.publicBySlug(slug);
    }

    @GetMapping("/admin/products")
    public List<ProductResponse> adminProducts() {
        return products.listAdmin();
    }

    @PostMapping("/admin/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return products.create(request);
    }

    @PutMapping("/admin/products/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @DeleteMapping("/admin/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        products.delete(id);
    }
}