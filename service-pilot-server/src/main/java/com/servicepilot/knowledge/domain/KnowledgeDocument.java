package com.servicepilot.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private KnowledgeDocumentStatus status;

    private Integer chunkCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
