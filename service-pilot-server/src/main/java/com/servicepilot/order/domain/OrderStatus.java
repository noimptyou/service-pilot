package com.servicepilot.order.domain;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING_PAYMENT("待付款"),
    PAID("已付款"),
    PROCESSING("处理中"),
    SHIPPED("已发货"),
    DELIVERED("已送达"),
    CANCELLED("已取消"),
    REFUNDED("已退款");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

}
