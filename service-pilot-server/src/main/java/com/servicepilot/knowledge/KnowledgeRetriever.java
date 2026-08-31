package com.servicepilot.knowledge;

import java.util.List;

public interface KnowledgeRetriever {

    List<KnowledgeReference> search(String query);
}
