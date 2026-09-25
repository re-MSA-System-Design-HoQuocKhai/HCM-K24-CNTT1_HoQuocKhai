package com.shopmart.payment.service;

import com.shopmart.payment.config.PaymentProperties;
import com.shopmart.payment.dto.PaymentRequest;
import com.shopmart.payment.dto.PaymentResponse;
import com.shopmart.payment.entity.Payment;
import com.shopmart.payment.entity.PaymentStatus;
import com.shopmart.payment.repository.PaymentRepository;
import com.shopmart.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProperties paymentProperties;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    @DisplayName("Thanh toán thành công khi số tiền trong hạn mức")
    void processPayment_shouldSuccess_whenAmountWithinLimit() {
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentProperties.simulateFailure()).thenReturn(false);
        when(paymentProperties.maxAmount()).thenReturn(new BigDecimal("80000000"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse result = paymentService.processPayment(
                new PaymentRequest(1L, new BigDecimal("50000000")));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Thanh toán thất bại khi simulate-failure=true")
    void processPayment_shouldFail_whenSimulateFailure() {
        when(paymentRepository.findByOrderId(2L)).thenReturn(Optional.empty());
        when(paymentProperties.simulateFailure()).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        PaymentResponse result = paymentService.processPayment(
                new PaymentRequest(2L, new BigDecimal("50000000")));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("Thanh toán thất bại khi vượt hạn mức (Saga rollback trigger)")
    void processPayment_shouldFail_whenAmountExceedsLimit() {
        when(paymentRepository.findByOrderId(3L)).thenReturn(Optional.empty());
        when(paymentProperties.simulateFailure()).thenReturn(false);
        when(paymentProperties.maxAmount()).thenReturn(new BigDecimal("80000000"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(3L);
            return p;
        });

        PaymentResponse result = paymentService.processPayment(
                new PaymentRequest(3L, new BigDecimal("84000000")));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getMessage()).contains("hạn mức");
    }

    @Test
    @DisplayName("Hoàn tiền thành công")
    void refund_shouldSetRefunded() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(1L)
                .amount(new BigDecimal("50000000"))
                .status(PaymentStatus.SUCCESS)
                .build();
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = paymentService.refund(1L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("Hoàn tiền thất bại khi giao dịch chưa SUCCESS")
    void refund_shouldThrow_whenNotSuccess() {
        Payment payment = Payment.builder()
                .id(1L)
                .orderId(1L)
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.refund(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
