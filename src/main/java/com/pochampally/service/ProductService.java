package com.pochampally.service;

import com.pochampally.config.CacheConfig;
import com.pochampally.entity.Product;
import com.pochampally.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    @Cacheable(value = CacheConfig.PRODUCT_LISTS_CACHE, key = "'active'")
    public List<Product> listActive() {
        return productRepository.findByIsActiveTrue();
    }

    public List<Product> listAll() {
        return productRepository.findAllNotDeleted();
    }

    @Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public Product getById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public List<Product> findRelated(String productId, int limit) {
        Product product = getById(productId);
        var pageable = org.springframework.data.domain.PageRequest.of(0, limit * 3);
        List<Product> candidates = productRepository.findRelated(
                productId,
                product.getFabric() != null ? product.getFabric() : Product.Fabric.SILK,
                product.getWeaveType() != null ? product.getWeaveType() : Product.WeaveType.HANDLOOM,
                pageable);
        java.util.Collections.shuffle(candidates);
        return candidates.stream().limit(limit).toList();
    }

    public Product getBySku(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Product not found for SKU: " + sku));
    }

    public List<Product> filterByFabric(Product.Fabric fabric) {
        return productRepository.findByFabricAndIsActiveTrue(fabric);
    }

    public List<Product> filterByWeaveType(Product.WeaveType weaveType) {
        return productRepository.findByWeaveTypeAndIsActiveTrue(weaveType);
    }

    public List<Product> filterByColor(String color) {
        return productRepository.findByBodyColorAndIsActiveTrue(color);
    }

    public List<Product> filterByPriceRange(Long minPrice, Long maxPrice) {
        return productRepository.findByPriceRange(minPrice, maxPrice);
    }

    public List<Product> search(String query) {
        return productRepository.searchByNameContaining(query);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    public Product create(Product product) {
        if (product.getSku() == null || product.getSku().isBlank()) {
            product.setSku(generateSku(product));
        }
        return productRepository.save(product);
    }

    private String generateSku(Product product) {
        String prefix = "DHN";
        String fabric = product.getFabric() != null ? product.getFabric().name().substring(0, Math.min(3, product.getFabric().name().length())) : "GEN";
        long count = productRepository.count() + 1;
        return (prefix + "-" + fabric + "-" + String.format("%04d", count)).toUpperCase();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id"),
            @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    })
    public Product update(String id, Product updates) {
        Product existing = getById(id);
        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setSecondaryDescription(updates.getSecondaryDescription());
        existing.setCategory(updates.getCategory());
        existing.setFabric(updates.getFabric());
        existing.setWeaveType(updates.getWeaveType());
        existing.setBodyColor(updates.getBodyColor());
        existing.setSize(updates.getSize());
        existing.setLengthMeters(updates.getLengthMeters());
        existing.setBlousePiece(updates.getBlousePiece());
        existing.setMrp(updates.getMrp());
        existing.setSellingPrice(updates.getSellingPrice());
        existing.setDiscountPct(updates.getDiscountPct());
        existing.setStock(updates.getStock());
        existing.setImages(updates.getImages());
        existing.setVideoUrl(updates.getVideoUrl());
        existing.setHsnCode(updates.getHsnCode());
        existing.setGstPct(updates.getGstPct());
        existing.setIsActive(updates.getIsActive());
        existing.setWeightGrams(updates.getWeightGrams());
        existing.setWidthInches(updates.getWidthInches());
        existing.setBlouseLengthMeters(updates.getBlouseLengthMeters());
        existing.setOccasion(updates.getOccasion());
        existing.setWorkType(updates.getWorkType());
        existing.setPattern(updates.getPattern());
        existing.setBodyColor(updates.getBodyColor());
        existing.setBorderColor(updates.getBorderColor());
        existing.setPalluColor(updates.getPalluColor());
        existing.setCareInstructions(updates.getCareInstructions());
        existing.setCertification(updates.getCertification());
        existing.setSku(updates.getSku());
        existing.setTags(updates.getTags());
        return productRepository.save(existing);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id"),
            @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    })
    public Product toggleActive(String id) {
        Product product = getById(id);
        product.setIsActive(!product.getIsActive());
        return productRepository.save(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#id"),
            @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    })
    public void softDelete(String id) {
        Product product = getById(id);
        product.setIsDeleted(true);
        product.setIsActive(false);
        productRepository.save(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#productId"),
            @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    })
    public void updateImages(String productId, List<String> images) {
        Product product = getById(productId);
        product.setImages(images);
        productRepository.save(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#productId"),
            @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    })
    public void updateVideoUrl(String productId, String videoUrl) {
        Product product = getById(productId);
        product.setVideoUrl(videoUrl);
        productRepository.save(product);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#productId"),
            @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    })
    public void decrementStock(String productId, int quantity) {
        int updatedRows = productRepository.decrementStock(productId, quantity);
        if (updatedRows == 0) {
            throw new IllegalStateException("Out of stock for product: " + productId);
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.PRODUCTS_CACHE, key = "#productId"),
            @CacheEvict(value = CacheConfig.PRODUCT_LISTS_CACHE, allEntries = true)
    })
    public void incrementStock(String productId, int quantity) {
        productRepository.incrementStock(productId, quantity);
    }
}
