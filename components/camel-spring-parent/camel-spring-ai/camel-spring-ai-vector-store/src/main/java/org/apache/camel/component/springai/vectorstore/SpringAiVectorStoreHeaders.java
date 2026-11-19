/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.springai.vectorstore;

import org.apache.camel.spi.Metadata;

public class SpringAiVectorStoreHeaders {
    // Operation control
    @Metadata(description = "The operation to perform (ADD, DELETE, SIMILARITY_SEARCH, UPSERT)",
              javaType = "org.apache.camel.component.springai.vectorstore.SpringAiVectorStoreOperation")
    public static final String OPERATION = "CamelSpringAiVectorStoreOperation";

    // Search parameters
    @Metadata(description = "The maximum number of similar documents to return (topK)", javaType = "Integer")
    public static final String TOP_K = "CamelSpringAiVectorStoreTopK";

    @Metadata(description = "The similarity threshold (0-1)", javaType = "Double")
    public static final String SIMILARITY_THRESHOLD = "CamelSpringAiVectorStoreSimilarityThreshold";

    @Metadata(description = "Filter expression for metadata-based filtering", javaType = "String")
    public static final String FILTER_EXPRESSION = "CamelSpringAiVectorStoreFilterExpression";

    // Upsert parameters: not the DocumentIds header, which ADD, UPSERT and SIMILARITY_SEARCH set to their output ids
    @Metadata(description = "The id of the document that UPSERT writes from a float[] body: the document with this id is"
                            + " replaced. Without it, the document gets a random UUID",
              javaType = "String")
    public static final String UPSERT_DOCUMENT_ID = "CamelSpringAiVectorStoreUpsertDocumentId";

    @Metadata(description = "The ids of the documents that UPSERT writes from a List of float[] body, one per vector: the"
                            + " documents with these ids are replaced. Without it, each document gets a random UUID",
              javaType = "java.util.List<String>")
    public static final String UPSERT_DOCUMENT_IDS = "CamelSpringAiVectorStoreUpsertDocumentIds";

    // Delete parameters / Results
    @Metadata(description = "List of document IDs (output of ADD, UPSERT and SIMILARITY_SEARCH, and input of DELETE when the"
                            + " message has no body)",
              javaType = "java.util.List<String>")
    public static final String DOCUMENT_IDS = "CamelSpringAiVectorStoreDocumentIds";

    // Results
    @Metadata(description = "List of similar documents found",
              javaType = "java.util.List<org.springframework.ai.document.Document>")
    public static final String SIMILAR_DOCUMENTS = "CamelSpringAiVectorStoreSimilarDocuments";

    @Metadata(description = "Number of documents added by ADD, or written by UPSERT", javaType = "Integer")
    public static final String DOCUMENTS_ADDED = "CamelSpringAiVectorStoreDocumentsAdded";

    @Metadata(description = "Number of document IDs that DELETE gave to the vector store, as the vector store does not tell how many"
                            + " of them existed",
              javaType = "Integer")
    public static final String DOCUMENTS_DELETED = "CamelSpringAiVectorStoreDocumentsDeleted";
}
