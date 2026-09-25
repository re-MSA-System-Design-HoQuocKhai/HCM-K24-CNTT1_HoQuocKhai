package com.shopmart.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Cấu hình giả lập cổng thanh toán.
 *
 * @param simulateFailure true  -> mọi giao dịch đều thất bại (dùng để chứng minh Saga rollback)
 * @param maxAmount       giao dịch có số tiền lớn hơn giá trị này sẽ thất bại
 */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(boolean simulateFailure, BigDecimal maxAmount) {
}
