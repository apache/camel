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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.springframework.ai.vectorstore.VectorStore;

@Configurer
@UriParams
public class SpringAiVectorStoreConfiguration implements Cloneable {

    @Metadata(required = true, autowired = true)
    @UriParam(description = "The Spring AI org.springframework.ai.vectorstore.VectorStore that the producer uses, such as a"
                            + " Qdrant or PGvector vector store. For ADD and for SIMILARITY_SEARCH of a text, the vector store"
                            + " embeds the text with its own embedding model.")
    private VectorStore vectorStore;

    @UriParam(defaultValue = "ADD",
              description = "The operation that the producer performs: ADD adds documents, which the vector store embeds;"
                            + " UPSERT writes documents with their vectors as they are, and replaces a document with the"
                            + " same id; DELETE deletes documents by id; SIMILARITY_SEARCH finds the documents most similar"
                            + " to a query. The CamelSpringAiVectorStoreOperation header replaces this option.")
    private SpringAiVectorStoreOperation operation = SpringAiVectorStoreOperation.ADD;

    @UriParam(defaultValue = "5",
              description = "The maximum number of documents that SIMILARITY_SEARCH returns. The value must not be"
                            + " negative. The CamelSpringAiVectorStoreTopK header replaces this option.")
    private int topK = 5;

    @UriParam(defaultValue = "0.0",
              description = "The minimum similarity score, from 0 to 1, of the documents that SIMILARITY_SEARCH returns."
                            + " The default 0.0 accepts all documents. The CamelSpringAiVectorStoreSimilarityThreshold"
                            + " header replaces this option.")
    private double similarityThreshold = 0.0;

    @UriParam(description = "The filter on the metadata of the documents for SIMILARITY_SEARCH, in the portable filter"
                            + " expression syntax of Spring AI, for example country == 'UK' && year >= 2020. The"
                            + " CamelSpringAiVectorStoreFilterExpression header replaces this option. DELETE does not"
                            + " delete by filter.")
    private String filterExpression;

    @UriParam(defaultValue = "false",
              description = "Whether SIMILARITY_SEARCH of a float[] body, such as the vector of spring-ai-embeddings,"
                            + " searches with this vector. By default it searches for the text of the"
                            + " CamelSpringAiEmbeddingInputText header, which the vector store embeds again. The vector"
                            + " store must support the search with a vector, and the vector must come from the same"
                            + " embedding model as the stored vectors.")
    private boolean searchByVector;

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public SpringAiVectorStoreOperation getOperation() {
        return operation;
    }

    public void setOperation(SpringAiVectorStoreOperation operation) {
        this.operation = operation;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public String getFilterExpression() {
        return filterExpression;
    }

    public void setFilterExpression(String filterExpression) {
        this.filterExpression = filterExpression;
    }

    public boolean isSearchByVector() {
        return searchByVector;
    }

    public void setSearchByVector(boolean searchByVector) {
        this.searchByVector = searchByVector;
    }

    public SpringAiVectorStoreConfiguration copy() {
        try {
            return (SpringAiVectorStoreConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
