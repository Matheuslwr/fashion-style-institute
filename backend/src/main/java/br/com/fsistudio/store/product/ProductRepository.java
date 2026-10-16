package br.com.fsistudio.store.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @EntityGraph(attributePaths = {"category", "images"})
    List<Product> findAllByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = {"category", "images"})
    List<Product> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = {"variants"})
    Optional<Product> findBySlug(String slug);

    @EntityGraph(attributePaths = {"category", "images"})
    Optional<Product> findById(UUID id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @Query(value = "SELECT id FROM products WHERE slug = :slug FOR UPDATE", nativeQuery = true)
    Optional<UUID> lockIdBySlug(@Param("slug") String slug);

    @Query(value = "SELECT id FROM products WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<UUID> lockIdById(@Param("id") UUID id);
}