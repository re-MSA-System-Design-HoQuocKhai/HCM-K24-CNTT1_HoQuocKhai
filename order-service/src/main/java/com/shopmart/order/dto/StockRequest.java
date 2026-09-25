package com.shopmart.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body gửi sang inventory-service khi trừ/hoàn tồn kho
 * (PUT /api/inventory/products/{id}/decrease | /increase).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockRequest {

    private Integer quantity;
}
