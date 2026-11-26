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
    @UriParam
    private VectorStore vectorStore;

    @UriParam(defaultValue = "ADD")
    private SpringAiVectorStoreOperation operation = SpringAiVectorStoreOperation.ADD;

    @UriParam(defaultValue = "5")
    private int topK = 5;

    @UriParam(defaultValue = "0.0")
    private double similarityThreshold = 0.0;

    @UriParam
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

    /**
     * The {@link VectorStore} to use for vector operations.
     */
    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public SpringAiVectorStoreOperation getOperation() {
        return operation;
    }

    /**
     * The operation to perform on the vector store (ADD, DELETE, SIMILARITY_SEARCH, UPSERT).
     */
    public void setOperation(SpringAiVectorStoreOperation operation) {
        this.operation = operation;
    }

    public int getTopK() {
        return topK;
    }

    /**
     * The maximum number of similar documents to return for similarity search.
     */
    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    /**
     * The minimum similarity score threshold (0-1) for similarity search.
     */
    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public String getFilterExpression() {
        return filterExpression;
    }

    /**
     * Filter expression for metadata-based filtering in searches.
     */
    public void setFilterExpression(String filterExpression) {
        this.filterExpression = filterExpression;
    }

    public boolean isSearchByVector() {
        return searchByVector;
    }

    /**
     * Whether SIMILARITY_SEARCH of a float[] body searches with this vector, instead of the text of the
     * CamelSpringAiEmbeddingInputText header.
     */
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
