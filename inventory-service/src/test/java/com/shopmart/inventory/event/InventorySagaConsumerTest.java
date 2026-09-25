package com.shopmart.inventory.event;

import com.shopmart.inventory.dto.ProductResponse;
import com.shopmart.inventory.exception.InsufficientStockException;
import com.shopmart.inventory.service.ProductService;
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
class InventorySagaConsumerTest {

    @Mock
    private ProductService productService;

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @InjectMocks
    private InventorySagaConsumer inventorySagaConsumer;

    @Test
    @DisplayName("Saga: ORDER_CREATED đủ tồn kho → trừ kho và phát INVENTORY_RESERVED")
    void handleOrderCreated_success_shouldPublishInventoryReserved() {
        OrderEvent event = OrderEvent.builder()
                .orderId(1L)
                .productId(1L)
                .quantity(2)
                .amount(new BigDecimal("50000000"))
                .type(SagaEventType.ORDER_CREATED)
                .build();

        ProductResponse response = ProductResponse.builder().id(1L).stock(8).build();
        when(productService.decreaseStock(1L, 2)).thenReturn(response);
        when(kafkaTemplate.send(eq(KafkaTopics.ORDER), eq("1"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        inventorySagaConsumer.handleSagaEvent(event);

        verify(productService).decreaseStock(1L, 2);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER), eq("1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(SagaEventType.INVENTORY_RESERVED);
    }

    @Test
    @DisplayName("Saga: ORDER_CREATED thiếu tồn kho → phát INVENTORY_FAILED")
    void handleOrderCreated_insufficientStock_shouldPublishInventoryFailed() {
        OrderEvent event = OrderEvent.builder()
                .orderId(2L)
                .productId(1L)
                .quantity(99)
                .type(SagaEventType.ORDER_CREATED)
                .build();

        when(productService.decreaseStock(1L, 99))
                .thenThrow(new InsufficientStockException("Sản phẩm không đủ tồn kho"));
        when(kafkaTemplate.send(eq(KafkaTopics.ORDER), eq("2"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        inventorySagaConsumer.handleSagaEvent(event);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER), eq("2"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(SagaEventType.INVENTORY_FAILED);
    }

    @Test
    @DisplayName("Saga Compensating: Nhận INVENTORY_RELEASED → Tăng lại tồn kho (Rollback)")
    void handleInventoryReleased_shouldIncreaseStock() {
        OrderEvent event = OrderEvent.builder()
                .orderId(3L)
                .productId(1L)
                .quantity(2)
                .type(SagaEventType.INVENTORY_RELEASED)
                .build();

        ProductResponse response = ProductResponse.builder().id(1L).stock(10).build();
        when(productService.increaseStock(1L, 2)).thenReturn(response);

        inventorySagaConsumer.handleSagaEvent(event);

        verify(productService).increaseStock(1L, 2);
    }
}
