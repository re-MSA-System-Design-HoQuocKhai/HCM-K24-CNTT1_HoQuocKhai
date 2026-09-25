package com.shopmart.inventory.event;

import com.shopmart.inventory.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer trong inventory-service: xử lý các sự kiện Saga.
 * Choreography Saga:
 *   - Nhận ORDER_CREATED → trừ tồn kho → publish INVENTORY_RESERVED hoặc INVENTORY_FAILED
 *   - Nhận INVENTORY_RELEASED → hoàn tồn kho (compensating transaction)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySagaConsumer {

    private final ProductService productService;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.ORDER, groupId = "inventory-service-saga")
    public void handleSagaEvent(OrderEvent event) {
        log.info("[INVENTORY-SERVICE] Received event: type={}, orderId={}, productId={}, qty={}",
                event.getType(), event.getOrderId(), event.getProductId(), event.getQuantity());

        switch (event.getType()) {
            case ORDER_CREATED -> handleOrderCreated(event);
            case INVENTORY_RELEASED -> handleInventoryReleased(event);
            default -> log.debug("[INVENTORY-SERVICE] Ignoring event type={}", event.getType());
        }
    }

    private void handleOrderCreated(OrderEvent event) {
        try {
            // Trừ tồn kho
            productService.decreaseStock(event.getProductId(), event.getQuantity());
            log.info("[INVENTORY-SERVICE] Stock reserved for order id={}, product id={}, qty={}",
                    event.getOrderId(), event.getProductId(), event.getQuantity());

            // Publish INVENTORY_RESERVED → payment-service sẽ xử lý thanh toán
            OrderEvent reservedEvent = OrderEvent.builder()
                    .orderId(event.getOrderId())
                    .productId(event.getProductId())
                    .quantity(event.getQuantity())
                    .amount(event.getAmount())
                    .type(SagaEventType.INVENTORY_RESERVED)
                    .message("Tồn kho đã được trừ thành công")
                    .build();
            kafkaTemplate.send(KafkaTopics.ORDER, String.valueOf(event.getOrderId()), reservedEvent);
            log.info("[INVENTORY-SERVICE] Published INVENTORY_RESERVED for order id={}", event.getOrderId());

        } catch (Exception ex) {
            log.error("[INVENTORY-SERVICE] Failed to reserve stock for order id={}: {}",
                    event.getOrderId(), ex.getMessage());

            // Publish INVENTORY_FAILED → order-service sẽ hủy đơn
            OrderEvent failedEvent = OrderEvent.builder()
                    .orderId(event.getOrderId())
                    .productId(event.getProductId())
                    .quantity(event.getQuantity())
                    .amount(event.getAmount())
                    .type(SagaEventType.INVENTORY_FAILED)
                    .message(ex.getMessage())
                    .build();
            kafkaTemplate.send(KafkaTopics.ORDER, String.valueOf(event.getOrderId()), failedEvent);
            log.info("[INVENTORY-SERVICE] Published INVENTORY_FAILED for order id={}", event.getOrderId());
        }
    }

    private void handleInventoryReleased(OrderEvent event) {
        try {
            // Hoàn tồn kho (compensating transaction)
            productService.increaseStock(event.getProductId(), event.getQuantity());
            log.info("[INVENTORY-SERVICE] Stock released (compensated) for order id={}, product id={}, qty={}",
                    event.getOrderId(), event.getProductId(), event.getQuantity());
        } catch (Exception ex) {
            log.error("[INVENTORY-SERVICE] Failed to release stock for order id={}: {}",
                    event.getOrderId(), ex.getMessage());
        }
    }
}
