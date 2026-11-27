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
    @Metadata(description = "The operation that the producer performs. It replaces the operation option.",
              javaType = "org.apache.camel.component.springai.vectorstore.SpringAiVectorStoreOperation")
    public static final String OPERATION = "CamelSpringAiVectorStoreOperation";

    // Search parameters
    @Metadata(description = "The maximum number of documents that SIMILARITY_SEARCH returns. It replaces the topK option.",
              javaType = "Integer")
    public static final String TOP_K = "CamelSpringAiVectorStoreTopK";

    @Metadata(description = "The minimum similarity score, from 0 to 1, of the documents that SIMILARITY_SEARCH returns. It"
                            + " replaces the similarityThreshold option.",
              javaType = "Double")
    public static final String SIMILARITY_THRESHOLD = "CamelSpringAiVectorStoreSimilarityThreshold";

    @Metadata(description = "The filter on the metadata of the documents for SIMILARITY_SEARCH, in the portable filter"
                            + " expression syntax of Spring AI. It replaces the filterExpression option.",
              javaType = "String")
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
    @Metadata(description = "The ids of the documents that ADD or UPSERT wrote, or that SIMILARITY_SEARCH found. DELETE"
                            + " reads the ids to delete from this header when the message has no body.",
              javaType = "java.util.List<String>")
    public static final String DOCUMENT_IDS = "CamelSpringAiVectorStoreDocumentIds";

    // Results
    @Metadata(description = "The documents that SIMILARITY_SEARCH found. The message body holds the same List.",
              javaType = "java.util.List<org.springframework.ai.document.Document>")
    public static final String SIMILAR_DOCUMENTS = "CamelSpringAiVectorStoreSimilarDocuments";

    @Metadata(description = "Number of documents added by ADD, or written by UPSERT", javaType = "Integer")
    public static final String DOCUMENTS_ADDED = "CamelSpringAiVectorStoreDocumentsAdded";

    @Metadata(description = "Number of document IDs that DELETE gave to the vector store, as the vector store does not tell how many"
                            + " of them existed",
              javaType = "Integer")
    public static final String DOCUMENTS_DELETED = "CamelSpringAiVectorStoreDocumentsDeleted";
}
