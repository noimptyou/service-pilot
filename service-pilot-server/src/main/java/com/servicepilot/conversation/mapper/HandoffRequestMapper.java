package com.servicepilot.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.servicepilot.conversation.domain.HandoffRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HandoffRequestMapper extends BaseMapper<HandoffRequest> {
}
