package com.servicepilot.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.servicepilot.order.OrderLookupResult;
import com.servicepilot.order.domain.CustomerOrder;
import com.servicepilot.order.mapper.CustomerOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final CustomerOrderMapper customerOrderMapper;

    @Transactional(readOnly = true)
    public OrderLookupResult findForCustomer(String customerName, String orderNumber) {
        String normalizedOrderNumber = orderNumber.trim().toUpperCase(Locale.ROOT);
        CustomerOrder order = customerOrderMapper.selectOne(
                Wrappers.<CustomerOrder>lambdaQuery()
                        .eq(CustomerOrder::getOrderNumber, normalizedOrderNumber)
                        .eq(CustomerOrder::getCustomerName, customerName)
        );
        if (order == null) {
            return new OrderLookupResult(
                    false,
                    normalizedOrderNumber,
                    null,
                    null,
                    null,
                    "未找到属于当前客户的订单，请检查订单编号"
            );
        }

        return new OrderLookupResult(
                true,
                order.getOrderNumber(),
                order.getProductName(),
                order.getStatus().getDescription(),
                order.getTrackingNumber(),
                "订单查询成功"
        );
    }
}
