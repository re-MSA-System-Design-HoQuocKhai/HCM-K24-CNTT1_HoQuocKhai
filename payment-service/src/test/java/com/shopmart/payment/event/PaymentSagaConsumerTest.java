package com.shopmart.payment.event;

import com.shopmart.payment.dto.PaymentRequest;
import com.shopmart.payment.dto.PaymentResponse;
import com.shopmart.payment.entity.PaymentStatus;
import com.shopmart.payment.service.PaymentService;
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
class PaymentSagaConsumerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @InjectMocks
    private PaymentSagaConsumer paymentSagaConsumer;

    @Test
    @DisplayName("Saga: INVENTORY_RESERVED thanh toán thành công → phát PAYMENT_COMPLETED")
    void handleInventoryReserved_success_shouldPublishPaymentCompleted() {
        OrderEvent event = OrderEvent.builder()
                .orderId(1L)
                .productId(1L)
                .quantity(2)
                .amount(new BigDecimal("50000000"))
                .type(SagaEventType.INVENTORY_RESERVED)
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .orderId(1L)
                .amount(new BigDecimal("50000000"))
                .status(PaymentStatus.SUCCESS)
                .message("Thanh toán thành công")
                .build();

        when(paymentService.processPayment(any(PaymentRequest.class))).thenReturn(response);
        when(kafkaTemplate.send(eq(KafkaTopics.ORDER), eq("1"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        paymentSagaConsumer.handleSagaEvent(event);

        verify(paymentService).processPayment(any(PaymentRequest.class));

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER), eq("1"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(SagaEventType.PAYMENT_COMPLETED);
    }

    @Test
    @DisplayName("Saga: INVENTORY_RESERVED thanh toán thất bại → phát PAYMENT_FAILED để kích hoạt rollback")
    void handleInventoryReserved_failure_shouldPublishPaymentFailed() {
        OrderEvent event = OrderEvent.builder()
                .orderId(2L)
                .productId(3L)
                .quantity(3)
                .amount(new BigDecimal("84000000"))
                .type(SagaEventType.INVENTORY_RESERVED)
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(2L)
                .orderId(2L)
                .amount(new BigDecimal("84000000"))
                .status(PaymentStatus.FAILED)
                .message("Số tiền vượt hạn mức 80000000")
                .build();

        when(paymentService.processPayment(any(PaymentRequest.class))).thenReturn(response);
        when(kafkaTemplate.send(eq(KafkaTopics.ORDER), eq("2"), any(OrderEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        paymentSagaConsumer.handleSagaEvent(event);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER), eq("2"), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(SagaEventType.PAYMENT_FAILED);
        assertThat(captor.getValue().getMessage()).contains("vượt hạn mức");
    }
}
