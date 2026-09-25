package com.shopmart.inventory.service.impl;

import com.shopmart.inventory.dto.ProductRequest;
import com.shopmart.inventory.dto.ProductResponse;
import com.shopmart.inventory.entity.Product;
import com.shopmart.inventory.exception.InsufficientStockException;
import com.shopmart.inventory.exception.ResourceNotFoundException;
import com.shopmart.inventory.repository.ProductRepository;
import com.shopmart.inventory.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    // TODO Câu 4: Cache kết quả - log dưới đây chỉ được in ra khi thực sự truy vấn DB
    @Override
    public ProductResponse getProductById(Long id) {
        log.info("Querying DB for product id={}", id);
        return ProductResponse.from(findProduct(id));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .price(request.getPrice())
                .stock(request.getStock())
                .build();
        Product saved = productRepository.save(product);
        log.info("Created product id={}", saved.getId());
        return ProductResponse.from(saved);
    }

    // TODO Câu 4: Cập nhật cache khi sửa sản phẩm
    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProduct(id);
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        log.info("Updated product id={}", id);
        return ProductResponse.from(productRepository.save(product));
    }

    // TODO Câu 4: Xoá cache khi xoá sản phẩm
    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        productRepository.delete(product);
        log.info("Deleted product id={}", id);
    }

    // TODO Câu 4: Tồn kho thay đổi -> cache phải được cập nhật hoặc xoá
    @Override
    @Transactional
    public ProductResponse decreaseStock(Long id, int quantity) {
        Product product = findProduct(id);
        if (product.getStock() < quantity) {
            log.error("Insufficient stock for product id={}: available={}, requested={}",
                    id, product.getStock(), quantity);
            throw new InsufficientStockException(
                    "Sản phẩm id=" + id + " không đủ tồn kho (còn " + product.getStock() + ")");
        }
        product.setStock(product.getStock() - quantity);
        log.info("Decreased stock of product id={} by {} -> {}", id, quantity, product.getStock());
        return ProductResponse.from(productRepository.save(product));
    }

    // TODO Câu 4: Tồn kho thay đổi -> cache phải được cập nhật hoặc xoá
    @Override
    @Transactional
    public ProductResponse increaseStock(Long id, int quantity) {
        Product product = findProduct(id);
        product.setStock(product.getStock() + quantity);
        log.info("Restored stock of product id={} by {} -> {}", id, quantity, product.getStock());
        return ProductResponse.from(productRepository.save(product));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm id=" + id));
    }
}
