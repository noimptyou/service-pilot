package com.servicepilot.knowledge.service;

import com.servicepilot.knowledge.domain.KnowledgeDocument;
import com.servicepilot.knowledge.domain.KnowledgeDocumentStatus;
import com.servicepilot.knowledge.dto.CreateKnowledgeRequest;
import com.servicepilot.knowledge.dto.KnowledgeDocumentResponse;
import com.servicepilot.knowledge.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService {

    private static final int CHUNK_SIZE = 400;

    private static final TokenTextSplitter TEXT_SPLITTER = TokenTextSplitter.builder()
            .withChunkSize(CHUNK_SIZE)
            .withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(1_000)
            .withKeepSeparator(true)
            .build();

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private final TransactionTemplate transactionTemplate;

    public KnowledgeDocumentResponse create(CreateKnowledgeRequest request) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "知识向量服务未启用");
        }

        KnowledgeDocument knowledgeDocument = transactionTemplate.execute(
                status -> saveProcessingDocument(request)
        );
        if (knowledgeDocument == null) {
            throw new IllegalStateException("保存知识文档失败");
        }

        try {
            List<Document> chunks = createVectorDocuments(knowledgeDocument);
            vectorStore.add(chunks);
            markDocumentReady(knowledgeDocument, chunks.size());
            return toResponse(knowledgeDocument);
        } catch (RuntimeException exception) {
            markDocumentFailed(knowledgeDocument, exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "知识文档向量化失败", exception);
        }
    }

    private KnowledgeDocument saveProcessingDocument(CreateKnowledgeRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        KnowledgeDocument knowledgeDocument = new KnowledgeDocument();
        knowledgeDocument.setTitle(request.getTitle().trim());
        knowledgeDocument.setContent(request.getContent().trim());
        knowledgeDocument.setStatus(KnowledgeDocumentStatus.PROCESSING);
        knowledgeDocument.setChunkCount(0);
        knowledgeDocument.setCreatedAt(now);
        knowledgeDocument.setUpdatedAt(now);

        if (knowledgeDocumentMapper.insert(knowledgeDocument) != 1) {
            throw new IllegalStateException("保存知识文档失败");
        }
        return knowledgeDocument;
    }

    private List<Document> createVectorDocuments(KnowledgeDocument knowledgeDocument) {
        List<Document> splitDocuments = TEXT_SPLITTER.apply(
                List.of(new Document(knowledgeDocument.getContent()))
        );
        if (splitDocuments.isEmpty()) {
            throw new IllegalStateException("知识内容无法生成有效切片");
        }

        return IntStream.range(0, splitDocuments.size())
                .mapToObj(index -> createVectorDocument(
                        knowledgeDocument,
                        splitDocuments.get(index),
                        index
                ))
                .toList();
    }

    private Document createVectorDocument(
            KnowledgeDocument knowledgeDocument,
            Document splitDocument,
            int chunkIndex
    ) {
        String chunkKey = "knowledge-%d-chunk-%d".formatted(
                knowledgeDocument.getId(),
                chunkIndex
        );

        return Document.builder()
                .id(UUID.nameUUIDFromBytes(chunkKey.getBytes(StandardCharsets.UTF_8)).toString())
                .text(splitDocument.getText())
                .metadata("knowledge_document_id", knowledgeDocument.getId().toString())
                .metadata("document_title", knowledgeDocument.getTitle())
                .metadata("chunk_index", chunkIndex)
                .metadata("source_type", "knowledge_document")
                .build();
    }

    private void markDocumentReady(KnowledgeDocument knowledgeDocument, int chunkCount) {
        transactionTemplate.executeWithoutResult(status -> {
            knowledgeDocument.setStatus(KnowledgeDocumentStatus.READY);
            knowledgeDocument.setChunkCount(chunkCount);
            knowledgeDocument.setUpdatedAt(OffsetDateTime.now());
            if (knowledgeDocumentMapper.updateById(knowledgeDocument) != 1) {
                throw new IllegalStateException("更新知识文档状态失败");
            }
        });
    }

    private void markDocumentFailed(KnowledgeDocument knowledgeDocument, RuntimeException originalException) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                knowledgeDocument.setStatus(KnowledgeDocumentStatus.FAILED);
                knowledgeDocument.setChunkCount(0);
                knowledgeDocument.setUpdatedAt(OffsetDateTime.now());
                if (knowledgeDocumentMapper.updateById(knowledgeDocument) != 1) {
                    throw new IllegalStateException("更新知识文档失败状态失败");
                }
            });
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(statusException);
        }
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument knowledgeDocument) {
        return new KnowledgeDocumentResponse(
                knowledgeDocument.getId(),
                knowledgeDocument.getTitle(),
                knowledgeDocument.getStatus(),
                knowledgeDocument.getChunkCount(),
                knowledgeDocument.getCreatedAt()
        );
    }
}
