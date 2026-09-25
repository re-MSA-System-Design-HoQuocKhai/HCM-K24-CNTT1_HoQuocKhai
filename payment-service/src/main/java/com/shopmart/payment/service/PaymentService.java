package com.shopmart.payment.service;

import com.shopmart.payment.dto.PaymentRequest;
import com.shopmart.payment.dto.PaymentResponse;

import java.util.List;

public interface PaymentService {

    /**
     * Thực hiện thanh toán cho đơn hàng. Kết quả luôn được lưu lại (SUCCESS hoặc FAILED),
     * phía gọi kiểm tra {@code status} để quyết định bước tiếp theo của Saga.
     */
    PaymentResponse processPayment(PaymentRequest request);

    /** Hoàn tiền - dùng làm compensating transaction trong Saga. */
    PaymentResponse refund(Long orderId);

    PaymentResponse getByOrderId(Long orderId);

    List<PaymentResponse> getAll();
}
