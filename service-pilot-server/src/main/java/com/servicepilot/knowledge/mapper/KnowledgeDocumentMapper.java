package com.servicepilot.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.servicepilot.knowledge.domain.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
