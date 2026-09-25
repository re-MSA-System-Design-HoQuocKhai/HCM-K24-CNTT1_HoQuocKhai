package com.shopmart.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Message trao đổi qua Kafka topic "order" (cấu trúc giống nhau ở cả 3 service).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent {

    private Long orderId;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private SagaEventType type;
    private String message;
}
