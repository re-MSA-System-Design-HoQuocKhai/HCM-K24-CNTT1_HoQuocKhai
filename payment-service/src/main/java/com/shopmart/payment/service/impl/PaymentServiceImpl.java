package com.shopmart.payment.service.impl;

import com.shopmart.payment.config.PaymentProperties;
import com.shopmart.payment.dto.PaymentRequest;
import com.shopmart.payment.dto.PaymentResponse;
import com.shopmart.payment.entity.Payment;
import com.shopmart.payment.entity.PaymentStatus;
import com.shopmart.payment.exception.ResourceNotFoundException;
import com.shopmart.payment.repository.PaymentRepository;
import com.shopmart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        paymentRepository.findByOrderId(request.getOrderId()).ifPresent(p -> {
            throw new IllegalStateException("Đơn hàng id=" + request.getOrderId() + " đã được xử lý thanh toán");
        });

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .amount(request.getAmount())
                .build();

        String failureReason = checkFailure(request);
        if (failureReason == null) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setMessage("Thanh toán thành công");
            log.info("Payment SUCCESS for orderId={}, amount={}", request.getOrderId(), request.getAmount());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setMessage(failureReason);
            log.error("Payment FAILED for orderId={}, amount={}: {}",
                    request.getOrderId(), request.getAmount(), failureReason);
        }
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Override
    @Transactional
    public PaymentResponse refund(Long orderId) {
        Payment payment = findByOrderId(orderId);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Chỉ hoàn tiền được giao dịch SUCCESS, hiện tại: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setMessage("Đã hoàn tiền");
        log.info("Payment REFUNDED for orderId={}, amount={}", orderId, payment.getAmount());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Override
    public PaymentResponse getByOrderId(Long orderId) {
        return PaymentResponse.from(findByOrderId(orderId));
    }

    @Override
    public List<PaymentResponse> getAll() {
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /** Giả lập cổng thanh toán: trả về lý do thất bại, hoặc null nếu thành công. */
    private String checkFailure(PaymentRequest request) {
        if (paymentProperties.simulateFailure()) {
            return "Cổng thanh toán lỗi (payment.simulate-failure=true)";
        }
        if (paymentProperties.maxAmount() != null
                && request.getAmount().compareTo(paymentProperties.maxAmount()) > 0) {
            return "Số tiền vượt hạn mức " + paymentProperties.maxAmount();
        }
        return null;
    }

    private Payment findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thanh toán cho orderId=" + orderId));
    }
}
