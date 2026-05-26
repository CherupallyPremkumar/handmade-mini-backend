package com.homebase.mini.product.service.store;

import org.chenile.utils.entity.service.EntityStore;
import com.homebase.mini.product.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.chenile.base.exception.NotFoundException;
import com.homebase.mini.product.configuration.dao.ProductRepository;
import java.util.Optional;

public class ProductEntityStore implements EntityStore<Product>{
    @Autowired private ProductRepository productRepository;

	@Override
	public void store(Product entity) {
        productRepository.save(entity);
	}

	@Override
	public Product retrieve(String id) {
        Optional<Product> entity = productRepository.findById(id);
        if (entity.isPresent()) return entity.get();
        throw new NotFoundException(1500,"Unable to find Product with ID " + id);
	}

}
