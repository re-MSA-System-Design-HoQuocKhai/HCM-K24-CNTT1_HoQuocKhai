package com.shopmart.payment.event;

import com.shopmart.payment.dto.PaymentRequest;
import com.shopmart.payment.dto.PaymentResponse;
import com.shopmart.payment.entity.PaymentStatus;
import com.shopmart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer trong payment-service: xử lý thanh toán trong Saga.
 * Choreography Saga:
 *   - Nhận INVENTORY_RESERVED → xử lý thanh toán → publish PAYMENT_COMPLETED hoặc PAYMENT_FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSagaConsumer {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @KafkaListener(topics = KafkaTopics.ORDER, groupId = "payment-service-saga")
    public void handleSagaEvent(OrderEvent event) {
        log.info("[PAYMENT-SERVICE] Received event: type={}, orderId={}, amount={}",
                event.getType(), event.getOrderId(), event.getAmount());

        switch (event.getType()) {
            case INVENTORY_RESERVED -> handleInventoryReserved(event);
            default -> log.debug("[PAYMENT-SERVICE] Ignoring event type={}", event.getType());
        }
    }

    private void handleInventoryReserved(OrderEvent event) {
        try {
            PaymentRequest paymentRequest = new PaymentRequest(event.getOrderId(), event.getAmount());
            PaymentResponse response = paymentService.processPayment(paymentRequest);

            if (response.getStatus() == PaymentStatus.SUCCESS) {
                log.info("[PAYMENT-SERVICE] Payment SUCCESS for order id={}", event.getOrderId());

                OrderEvent completedEvent = OrderEvent.builder()
                        .orderId(event.getOrderId())
                        .productId(event.getProductId())
                        .quantity(event.getQuantity())
                        .amount(event.getAmount())
                        .type(SagaEventType.PAYMENT_COMPLETED)
                        .message("Thanh toán thành công")
                        .build();
                kafkaTemplate.send(KafkaTopics.ORDER, String.valueOf(event.getOrderId()), completedEvent);
                log.info("[PAYMENT-SERVICE] Published PAYMENT_COMPLETED for order id={}", event.getOrderId());

            } else {
                log.error("[PAYMENT-SERVICE] Payment FAILED for order id={}: {}",
                        event.getOrderId(), response.getMessage());

                OrderEvent failedEvent = OrderEvent.builder()
                        .orderId(event.getOrderId())
                        .productId(event.getProductId())
                        .quantity(event.getQuantity())
                        .amount(event.getAmount())
                        .type(SagaEventType.PAYMENT_FAILED)
                        .message(response.getMessage())
                        .build();
                kafkaTemplate.send(KafkaTopics.ORDER, String.valueOf(event.getOrderId()), failedEvent);
                log.info("[PAYMENT-SERVICE] Published PAYMENT_FAILED for order id={}", event.getOrderId());
            }

        } catch (Exception ex) {
            log.error("[PAYMENT-SERVICE] Error processing payment for order id={}: {}",
                    event.getOrderId(), ex.getMessage());

            OrderEvent failedEvent = OrderEvent.builder()
                    .orderId(event.getOrderId())
                    .productId(event.getProductId())
                    .quantity(event.getQuantity())
                    .amount(event.getAmount())
                    .type(SagaEventType.PAYMENT_FAILED)
                    .message("Lỗi xử lý thanh toán: " + ex.getMessage())
                    .build();
            kafkaTemplate.send(KafkaTopics.ORDER, String.valueOf(event.getOrderId()), failedEvent);
        }
    }
}
