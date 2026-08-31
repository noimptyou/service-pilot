package com.servicepilot.knowledge;

public record KnowledgeReference(
        Long knowledgeDocumentId,
        String documentTitle,
        int chunkIndex,
        String content,
        Double score
) {
}
