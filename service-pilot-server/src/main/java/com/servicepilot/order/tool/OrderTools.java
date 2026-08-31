package com.servicepilot.order.tool;

import com.servicepilot.order.OrderLookupResult;
import com.servicepilot.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderTools {

    public static final String CUSTOMER_NAME_CONTEXT_KEY = "customerName";

    private final OrderService orderService;

    @Tool(
            name = "query_order",
            description = "根据订单编号查询当前客服会话客户本人的订单状态、商品和物流信息。客户询问具体订单时使用。"
    )
    public OrderLookupResult queryOrder(
            @ToolParam(description = "订单编号，例如 SP-20260830-1001") String orderNumber,
            ToolContext toolContext
    ) {
        String customerName = Objects.toString(
                toolContext.getContext().get(CUSTOMER_NAME_CONTEXT_KEY),
                ""
        ).trim();
        if (customerName.isEmpty()) {
            throw new IllegalStateException("缺少当前客户身份，不能查询订单");
        }
        return orderService.findForCustomer(customerName, orderNumber);
    }
}
