package com.servicepilot.knowledge.dto;

import com.servicepilot.knowledge.domain.KnowledgeDocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class KnowledgeDocumentResponse {

    private Long id;

    private String title;

    private KnowledgeDocumentStatus status;

    private Integer chunkCount;

    private OffsetDateTime createdAt;
}
