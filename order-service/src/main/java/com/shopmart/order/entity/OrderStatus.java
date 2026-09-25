package com.shopmart.order.entity;

public enum OrderStatus {
    /** Đơn vừa tạo, Saga đang xử lý. */
    PENDING,
    /** Tất cả các bước (trừ kho, thanh toán) thành công. */
    COMPLETED,
    /** Một bước thất bại, các bước trước đã được hoàn tác (compensate). */
    CANCELLED
}
