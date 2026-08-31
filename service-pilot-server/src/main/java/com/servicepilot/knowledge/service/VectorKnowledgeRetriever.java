package com.servicepilot.knowledge.service;

import com.servicepilot.knowledge.KnowledgeReference;
import com.servicepilot.knowledge.KnowledgeRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VectorKnowledgeRetriever implements KnowledgeRetriever {

    private static final int TOP_K = 3;

    private static final double SIMILARITY_THRESHOLD = 0.70;

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @Override
    public List<KnowledgeReference> search(String query) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            return List.of();
        }

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query.trim())
                    .topK(TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .filterExpression("source_type == 'knowledge_document'")
                    .build();

            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            if (documents == null || documents.isEmpty()) {
                return List.of();
            }
            return documents.stream()
                    .map(this::toReference)
                    .toList();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "知识检索暂时不可用", exception);
        }
    }

    private KnowledgeReference toReference(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new KnowledgeReference(
                toLong(metadata.get("knowledge_document_id")),
                Objects.toString(metadata.get("document_title"), "未知知识文档"),
                toInt(metadata.get("chunk_index")),
                document.getText(),
                document.getScore()
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(Objects.toString(value));
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(Objects.toString(value));
    }
}
