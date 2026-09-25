package com.shopmart.inventory.service;

import com.shopmart.inventory.dto.ProductRequest;
import com.shopmart.inventory.dto.ProductResponse;

import java.util.List;

public interface ProductService {

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long id);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);

    /** Trừ tồn kho khi đặt hàng. */
    ProductResponse decreaseStock(Long id, int quantity);

    /** Hoàn tồn kho - dùng làm compensating transaction trong Saga. */
    ProductResponse increaseStock(Long id, int quantity);
}
