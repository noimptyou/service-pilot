package com.servicepilot.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.servicepilot.conversation.domain.CustomerSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerSessionMapper extends BaseMapper<CustomerSession> {
}
