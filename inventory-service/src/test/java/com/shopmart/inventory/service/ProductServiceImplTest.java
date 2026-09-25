package com.shopmart.inventory.service;

import com.shopmart.inventory.dto.ProductResponse;
import com.shopmart.inventory.entity.Product;
import com.shopmart.inventory.exception.InsufficientStockException;
import com.shopmart.inventory.exception.ResourceNotFoundException;
import com.shopmart.inventory.repository.ProductRepository;
import com.shopmart.inventory.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test mẫu (Mockito, không cần DB). Sinh viên viết thêm test theo cùng pattern.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("iPhone 15 Pro")
                .price(new BigDecimal("25000000"))
                .stock(10)
                .build();
    }

    @Test
    void decreaseStock_shouldReduceStock_whenEnoughStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse result = productService.decreaseStock(1L, 3);

        assertThat(result.getStock()).isEqualTo(7);
    }

    @Test
    void decreaseStock_shouldThrow_whenNotEnoughStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 99))
                .isInstanceOf(InsufficientStockException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void increaseStock_shouldRestoreStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse result = productService.increaseStock(1L, 5);

        assertThat(result.getStock()).isEqualTo(15);
    }

    @Test
    void getProductById_shouldThrow_whenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
