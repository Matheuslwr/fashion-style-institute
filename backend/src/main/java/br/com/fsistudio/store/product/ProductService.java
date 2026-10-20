package br.com.fsistudio.store.product;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.category.Category;
import br.com.fsistudio.store.category.CategoryRepository;

@Service
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listPublic(String categorySlug, Boolean featured, String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.forLanguageTag("pt-BR"));
        return products.findAllByActiveTrueOrderByNameAsc().stream()
            .filter(product -> categorySlug == null || categorySlug.isBlank()
                || product.getCategory().getSlug().equals(categorySlug))
            .filter(product -> featured == null || product.isFeatured() == featured)
            .filter(product -> normalizedQuery.isBlank()
                || product.getName().toLowerCase(Locale.forLanguageTag("pt-BR")).contains(normalizedQuery)
                || product.getDescription().toLowerCase(Locale.forLanguageTag("pt-BR")).contains(normalizedQuery)
                || product.getDetails().toLowerCase(Locale.forLanguageTag("pt-BR")).contains(normalizedQuery))
            .map(ProductResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse publicBySlug(String slug) {
        return products.findBySlugAndActiveTrue(slug).map(ProductResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listAdmin() {
        return products.findAllByOrderByNameAsc().stream().map(ProductResponse::from).toList();
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        validatePrices(request);
        ProductType productType = request.productType() == null ? ProductType.STANDARD : request.productType();
        WatchMeasurements watchMeasurements = validateProductType(productType, request.watchMeasurements());
        Category category = category(request.categoryId());
        String slug = uniqueSlug(request.name(), null);
        Product product = new Product(request.name().trim(), slug, request.description().trim(), normalizeDetails(request.details()),
            productType, watchMeasurements, request.priceCents(),
            request.promotionalPriceCents(), request.featured(), request.active(), category);
        applyChildren(product, request);
        return ProductResponse.from(products.save(product));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        validatePrices(request);
        products.lockIdById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        Product product = products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        product.replaceImages(List.of());
        product.replaceVariants(List.of());
        products.flush();
        String details = request.details() == null ? product.getDetails() : normalizeDetails(request.details());
        ProductType productType = request.productType() == null ? product.getProductType() : request.productType();
        WatchMeasurements watchMeasurements = request.productType() == null && request.watchMeasurements() == null
            ? product.getWatchMeasurements() : validateProductType(productType, request.watchMeasurements());
        product.update(request.name().trim(), product.getSlug(), request.description().trim(), details, productType, watchMeasurements,
            request.priceCents(), request.promotionalPriceCents(), request.featured(), request.active(), category(request.categoryId()));
        applyChildren(product, request);
        return ProductResponse.from(products.save(product));
    }

    @Transactional
    public void delete(UUID id) {
        products.lockIdById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        Product product = products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        products.delete(product);
    }

    private void applyChildren(Product product, ProductRequest request) {
        product.replaceImages(request.images().stream()
            .map(image -> new ProductImage(image.imageUrl().trim(), image.altText().trim(), image.color(), image.displayOrder()))
            .toList());
        product.replaceVariants(request.variants().stream()
            .map(variant -> new ProductVariant(variant.sku().trim(), variant.color().trim(), variant.size().trim(), variant.stock()))
            .toList());
    }

    private void validatePrices(ProductRequest request) {
        if (request.promotionalPriceCents() != null && request.promotionalPriceCents() >= request.priceCents()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O preço promocional deve ser menor que o preço normal");
        }
    }

    private static String normalizeDetails(String details) {
        return details == null ? "" : details.trim();
    }

    private static WatchMeasurements validateProductType(ProductType productType, WatchMeasurements measurements) {
        if (productType == ProductType.WATCH) {
            if (measurements == null || !measurements.isComplete()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Preencha largura, comprimento, altura e medida do mostrador do relógio");
            }
            return measurements;
        }
        return null;
    }

    private Category category(UUID id) {
        return categories.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida"));
    }

    private String uniqueSlug(String name, UUID excludedId) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String slug = base;
        int suffix = 2;
        while (excludedId == null ? products.existsBySlug(slug) : products.existsBySlugAndIdNot(slug, excludedId)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}