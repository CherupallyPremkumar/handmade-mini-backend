package com.pochampally.service;

import com.pochampally.entity.Product;
import com.pochampally.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> listActive() {
        return productRepository.findByIsActiveTrue();
    }

    public Product getById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    public List<Product> filterByFabric(Product.Fabric fabric) {
        return productRepository.findByFabricAndIsActiveTrue(fabric);
    }

    public List<Product> filterByWeaveType(Product.WeaveType weaveType) {
        return productRepository.findByWeaveTypeAndIsActiveTrue(weaveType);
    }

    public List<Product> filterByColor(String color) {
        return productRepository.findByColorAndIsActiveTrue(color);
    }

    public List<Product> filterByPriceRange(Long minPrice, Long maxPrice) {
        return productRepository.findByPriceRange(minPrice, maxPrice);
    }

    public List<Product> search(String query) {
        return productRepository.searchByNameContaining(query);
    }

    @Transactional
    public Product create(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public Product update(String id, Product updates) {
        Product existing = getById(id);
        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setCategory(updates.getCategory());
        existing.setFabric(updates.getFabric());
        existing.setWeaveType(updates.getWeaveType());
        existing.setColor(updates.getColor());
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
        return productRepository.save(existing);
    }

    @Transactional
    public void softDelete(String id) {
        Product product = getById(id);
        product.setIsActive(false);
        productRepository.save(product);
    }

    @Transactional
    public void updateImages(String productId, List<String> images) {
        Product product = getById(productId);
        product.setImages(images);
        productRepository.save(product);
    }

    @Transactional
    public void updateVideoUrl(String productId, String videoUrl) {
        Product product = getById(productId);
        product.setVideoUrl(videoUrl);
        productRepository.save(product);
    }

    @Transactional
    public void decrementStock(String productId, int quantity) {
        int updatedRows = productRepository.decrementStock(productId, quantity);
        if (updatedRows == 0) {
            throw new IllegalStateException("Out of stock for product: " + productId);
        }
    }
}
