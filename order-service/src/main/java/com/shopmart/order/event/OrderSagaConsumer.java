package com.shopmart.order.event;

import com.shopmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer trong order-service: lắng nghe kết quả từ các bước Saga.
 * Choreography Saga flow:
 *   1. order-service tạo đơn → publish ORDER_CREATED
 *   2. inventory-service nhận → trừ kho → publish INVENTORY_RESERVED hoặc INVENTORY_FAILED
 *   3. payment-service nhận INVENTORY_RESERVED → thanh toán → publish PAYMENT_COMPLETED hoặc PAYMENT_FAILED
 *   4. order-service nhận PAYMENT_COMPLETED → COMPLETED
 *      order-service nhận PAYMENT_FAILED → publish INVENTORY_RELEASED (hoàn kho) + CANCELLED
 *      order-service nhận INVENTORY_FAILED → CANCELLED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaConsumer {

    private final OrderService orderService;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.ORDER, groupId = "order-service-saga")
    public void handleSagaEvent(OrderEvent event) {
        log.info("[ORDER-SERVICE] Received event: type={}, orderId={}, message={}",
                event.getType(), event.getOrderId(), event.getMessage());

        switch (event.getType()) {
            case INVENTORY_RESERVED -> {
                // Tồn kho đã được trừ → không cần xử lý thêm ở đây
                // payment-service sẽ tự lắng nghe INVENTORY_RESERVED
                log.info("[ORDER-SERVICE] Inventory reserved for order id={}, waiting for payment...",
                        event.getOrderId());
            }

            case PAYMENT_COMPLETED -> {
                // Thanh toán thành công → Hoàn tất đơn hàng
                log.info("[ORDER-SERVICE] Payment completed for order id={} → marking COMPLETED",
                        event.getOrderId());
                orderService.completeOrder(event.getOrderId());
            }

            case PAYMENT_FAILED -> {
                // Thanh toán thất bại → Hủy đơn + Yêu cầu hoàn tồn kho (compensating)
                log.error("[ORDER-SERVICE] Payment FAILED for order id={}: {}",
                        event.getOrderId(), event.getMessage());
                orderService.cancelOrder(event.getOrderId(), event.getMessage());

                // Phát sự kiện yêu cầu inventory-service hoàn tồn kho
                OrderEvent releaseEvent = OrderEvent.builder()
                        .orderId(event.getOrderId())
                        .productId(event.getProductId())
                        .quantity(event.getQuantity())
                        .amount(event.getAmount())
                        .type(SagaEventType.INVENTORY_RELEASED)
                        .message("Hoàn tồn kho do thanh toán thất bại")
                        .build();
                kafkaTemplate.send(KafkaTopics.ORDER, String.valueOf(event.getOrderId()), releaseEvent);
                log.info("[ORDER-SERVICE] Published INVENTORY_RELEASED for order id={}", event.getOrderId());
            }

            case INVENTORY_FAILED -> {
                // Tồn kho không đủ → Hủy đơn (không cần compensating vì chưa có bước nào thành công)
                log.error("[ORDER-SERVICE] Inventory FAILED for order id={}: {}",
                        event.getOrderId(), event.getMessage());
                orderService.cancelOrder(event.getOrderId(), event.getMessage());
            }

            default -> log.debug("[ORDER-SERVICE] Ignoring event type={}", event.getType());
        }
    }
}
