package com.servicepilot.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.servicepilot.order.domain.CustomerOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> {
}
