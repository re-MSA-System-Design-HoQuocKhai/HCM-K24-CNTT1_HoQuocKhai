package com.shopmart.order.client;

import com.shopmart.order.dto.ProductDto;
import com.shopmart.order.dto.StockRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Facade bọc InventoryClient với Resilience4j Circuit Breaker.
 *
 * Circuit Breaker có 3 trạng thái:
 *   CLOSED   → Hoạt động bình thường, đếm số lỗi. Khi tỷ lệ lỗi vượt ngưỡng → chuyển OPEN.
 *   OPEN     → Chặn tất cả request, trả về fallback ngay. Sau thời gian chờ → chuyển HALF_OPEN.
 *   HALF_OPEN→ Cho phép một số request thử lại. Nếu thành công → CLOSED, nếu thất bại → OPEN.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryServiceFacade {

    private final InventoryClient inventoryClient;

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getProductFallback")
    public ProductDto getProduct(Long productId) {
        log.info("Calling inventory-service to get product id={}", productId);
        return inventoryClient.getProduct(productId);
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "decreaseStockFallback")
    public ProductDto decreaseStock(Long productId, int quantity) {
        log.info("Calling inventory-service to decrease stock for product id={}, qty={}", productId, quantity);
        return inventoryClient.decreaseStock(productId, new StockRequest(quantity));
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "increaseStockFallback")
    public ProductDto increaseStock(Long productId, int quantity) {
        log.info("Calling inventory-service to increase stock for product id={}, qty={}", productId, quantity);
        return inventoryClient.increaseStock(productId, new StockRequest(quantity));
    }

    /**
     * Fallback khi inventory-service không khả dụng hoặc Circuit Breaker OPEN.
     */
    public ProductDto getProductFallback(Long productId, Throwable t) {
        log.error("CIRCUIT BREAKER FALLBACK - Cannot get product id={}: {}", productId, t.getMessage());
        return ProductDto.builder()
                .id(productId)
                .name("[Không khả dụng]")
                .price(java.math.BigDecimal.ZERO)
                .stock(0)
                .build();
    }

    public ProductDto decreaseStockFallback(Long productId, int quantity, Throwable t) {
        log.error("CIRCUIT BREAKER FALLBACK - Cannot decrease stock for product id={}: {}", productId, t.getMessage());
        throw new RuntimeException("Inventory service không khả dụng, không thể trừ tồn kho: " + t.getMessage());
    }

    public ProductDto increaseStockFallback(Long productId, int quantity, Throwable t) {
        log.error("CIRCUIT BREAKER FALLBACK - Cannot increase stock for product id={}: {}", productId, t.getMessage());
        throw new RuntimeException("Inventory service không khả dụng, không thể hoàn tồn kho: " + t.getMessage());
    }
}
