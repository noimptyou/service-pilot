package com.servicepilot.conversation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.servicepilot.conversation.domain.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
