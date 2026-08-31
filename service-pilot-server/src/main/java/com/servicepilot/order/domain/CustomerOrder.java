package com.servicepilot.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("customer_order")
public class CustomerOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNumber;

    private String customerName;

    private String productName;

    private OrderStatus status;

    private String trackingNumber;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
