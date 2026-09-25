package com.shopmart.inventory.controller;

import com.shopmart.inventory.dto.ProductRequest;
import com.shopmart.inventory.dto.ProductResponse;
import com.shopmart.inventory.dto.StockRequest;
import com.shopmart.inventory.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Value("${server.port}")
    private String port;

    /** Trả về port của instance đang xử lý request - dùng để minh chứng Load Balancing. */
    @GetMapping("/instance")
    public String instance() {
        return "inventory-service instance on port " + port;
    }

    @GetMapping("/products")
    public List<ProductResponse> getAll() {
        return productService.getAllProducts();
    }

    @GetMapping("/products/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/products/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/products/{id}/decrease")
    public ProductResponse decreaseStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        return productService.decreaseStock(id, request.getQuantity());
    }

    @PutMapping("/products/{id}/increase")
    public ProductResponse increaseStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        return productService.increaseStock(id, request.getQuantity());
    }
}
