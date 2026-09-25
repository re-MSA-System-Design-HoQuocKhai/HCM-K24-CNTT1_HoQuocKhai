package com.shopmart.order.event;

import com.shopmart.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaConsumerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @InjectMocks
    private OrderSagaConsumer orderSagaConsumer;

    @Test
    @DisplayName("Saga: Nhận PAYMENT_COMPLETED → Đổi trạng thái đơn sang COMPLETED")
    void handleSagaEvent_paymentCompleted_shouldCompleteOrder() {
        OrderEvent event = OrderEvent.builder()
                .orderId(10L)
                .productId(1L)
                .quantity(2)
                .amount(new BigDecimal("50000000"))
                .type(SagaEventType.PAYMENT_COMPLETED)
                .message("Thanh toán thành công")
                .build();

        orderSagaConsumer.handleSagaEvent(event);

        verify(orderService).completeOrder(10L);
    }

    @Test
    @DisplayName("Saga Rollback: Nhận PAYMENT_FAILED → Hủy đơn và gửi INVENTORY_RELEASED hoàn tồn kho")
    void handleSagaEvent_paymentFailed_shouldCancelOrderAndPublishInventoryReleased() {
        OrderEvent event = OrderEvent.builder()
                .orderId(20L)
                .productId(2L)
                .quantity(3)
                .amount(new BigDecimal("90000000"))
                .type(SagaEventType.PAYMENT_FAILED)
                .message("Số tiền vượt hạn mức 80000000")
                .build();

        when(kafkaTemplate.send(eq(KafkaTopics.ORDER), eq("20"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        orderSagaConsumer.handleSagaEvent(event);

        verify(orderService).cancelOrder(20L, "Số tiền vượt hạn mức 80000000");

        ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER), eq("20"), eventCaptor.capture());

        OrderEvent captured = eventCaptor.getValue();
        assertThat(captured.getType()).isEqualTo(SagaEventType.INVENTORY_RELEASED);
        assertThat(captured.getOrderId()).isEqualTo(20L);
        assertThat(captured.getProductId()).isEqualTo(2L);
        assertThat(captured.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Saga: Nhận INVENTORY_FAILED → Hủy đơn hàng")
    void handleSagaEvent_inventoryFailed_shouldCancelOrder() {
        OrderEvent event = OrderEvent.builder()
                .orderId(30L)
                .productId(1L)
                .quantity(100)
                .type(SagaEventType.INVENTORY_FAILED)
                .message("Không đủ tồn kho")
                .build();

        orderSagaConsumer.handleSagaEvent(event);

        verify(orderService).cancelOrder(30L, "Không đủ tồn kho");
    }
}
