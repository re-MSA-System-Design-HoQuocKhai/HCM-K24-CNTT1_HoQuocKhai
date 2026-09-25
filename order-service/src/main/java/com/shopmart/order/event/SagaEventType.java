package com.shopmart.order.event;

/**
 * Các loại sự kiện trong luồng Saga đặt hàng (giống nhau ở cả 3 service).
 */
public enum SagaEventType {
    ORDER_CREATED,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    INVENTORY_RELEASED
}
