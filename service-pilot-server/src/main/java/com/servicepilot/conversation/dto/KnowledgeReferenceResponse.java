package com.servicepilot.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KnowledgeReferenceResponse {

    private Long knowledgeDocumentId;

    private String documentTitle;

    private int chunkIndex;

    private String content;

    private Double score;
}
