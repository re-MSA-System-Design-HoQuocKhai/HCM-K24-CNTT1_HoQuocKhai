package com.shopmart.order.service;

import com.shopmart.order.client.InventoryServiceFacade;
import com.shopmart.order.dto.OrderRequest;
import com.shopmart.order.dto.OrderResponse;
import com.shopmart.order.dto.ProductDto;
import com.shopmart.order.entity.Order;
import com.shopmart.order.entity.OrderStatus;
import com.shopmart.order.event.KafkaTopics;
import com.shopmart.order.event.OrderEvent;
import com.shopmart.order.event.SagaEventType;
import com.shopmart.order.repository.OrderRepository;
import com.shopmart.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryServiceFacade inventoryFacade;

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    @DisplayName("Tạo đơn hàng → trạng thái PENDING, gửi Kafka event ORDER_CREATED")
    void createOrder_shouldSaveWithPendingStatus() {
        // Arrange
        ProductDto product = ProductDto.builder()
                .id(1L).name("iPhone 15 Pro")
                .price(new BigDecimal("25000000")).stock(50)
                .build();
        when(inventoryFacade.getProduct(1L)).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(1L);
            return order;
        });
        when(kafkaTemplate.send(eq(KafkaTopics.ORDER), any(String.class), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        OrderResponse result = orderService.createOrder(new OrderRequest("C001", 1L, 2));

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("50000000"));
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER), eq("1"), any(OrderEvent.class));
    }

    @Test
    @DisplayName("Hủy đơn hàng → trạng thái CANCELLED + lưu lý do")
    void cancelOrder_shouldSetCancelledWithReason() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = orderService.cancelOrder(1L, "Payment failed");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getFailureReason()).isEqualTo("Payment failed");
    }

    @Test
    @DisplayName("Saga Rollback: PAYMENT_FAILED → đơn hàng CANCELLED")
    void sagaRollback_paymentFailed_shouldCancelOrder() {
        // Arrange: đơn hàng đang PENDING
        Order order = Order.builder()
                .id(1L)
                .customerId("C001")
                .productId(1L)
                .quantity(2)
                .totalAmount(new BigDecimal("50000000"))
                .status(OrderStatus.PENDING)
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act: giả lập nhận PAYMENT_FAILED → gọi cancelOrder
        String failReason = "Số tiền vượt hạn mức 80000000";
        OrderResponse result = orderService.cancelOrder(1L, failReason);

        // Assert
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getFailureReason()).contains("vượt hạn mức");
    }

    @Test
    @DisplayName("Hoàn tất đơn hàng → trạng thái COMPLETED")
    void completeOrder_shouldSetCompleted() {
        Order order = Order.builder().id(1L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse result = orderService.completeOrder(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }
}
