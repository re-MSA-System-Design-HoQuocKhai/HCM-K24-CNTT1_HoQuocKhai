package com.shopmart.payment.service;

import com.shopmart.payment.config.PaymentProperties;
import com.shopmart.payment.dto.PaymentRequest;
import com.shopmart.payment.dto.PaymentResponse;
import com.shopmart.payment.entity.Payment;
import com.shopmart.payment.entity.PaymentStatus;
import com.shopmart.payment.repository.PaymentRepository;
import com.shopmart.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentServiceImpl serviceWith(boolean simulateFailure) {
        return new PaymentServiceImpl(paymentRepository,
                new PaymentProperties(simulateFailure, new BigDecimal("80000000")));
    }

    @Test
    void processPayment_shouldSucceed_whenAmountWithinLimit() {
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = serviceWith(false)
                .processPayment(new PaymentRequest(1L, new BigDecimal("25000000")));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void processPayment_shouldFail_whenSimulateFailureEnabled() {
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse result = serviceWith(true)
                .processPayment(new PaymentRequest(1L, new BigDecimal("1000")));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    // TODO Câu 5: Viết thêm test cho refund()
}
