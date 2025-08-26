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
package org.apache.camel.component.langchain4j.embeddingstore;

import org.apache.camel.spi.Metadata;

/**
 * Constants and header definitions for LangChain4j embedding store component.
 *
 * <p>
 * This class defines the component scheme, default values, and message headers used for embedding store operations.
 * </p>
 */
public class LangChain4jEmbeddingStore {
    /** Component URI scheme for embedding store endpoints */
    public static final String SCHEME = "langchain4j-embeddingstore";

    /** Default dimension for embedding vectors (typically used by MiniLM models) */
    public static final int DEFAULT_COLLECTION_DIMENSION = 384;

    /** Default maximum number of results returned by search operations */
    public static final String DEFAULT_MAX_RESULTS = "5";

    private LangChain4jEmbeddingStore() {
    }

    /**
     * Message headers used by the embedding store component.
     *
     * <p>
     * These headers control the behavior of embedding store operations and provide additional parameters for search and
     * filter operations.
     * </p>
     */
    public static class Headers {
        /** Specifies the embedding store operation to perform (ADD, REMOVE, or SEARCH) */
        @Metadata(description = "The action to be performed.", javaType = "String",
                  enums = "ADD,REMOVE,SEARCH")
        public static final String ACTION = "CamelLangchain4jEmbeddingStoreAction";

        /** Maximum number of results to return from search operations */
        @Metadata(description = "Maximum number of search results to return", javaType = "Integer",
                  defaultValue = DEFAULT_MAX_RESULTS)
        public static final String MAX_RESULTS = "CamelLangchain4jEmbeddingStoreMaxResults";

        /** Minimum similarity score threshold for search results */
        @Metadata(description = "Minimum similarity score for search results", javaType = "Double")
        public static final String MIN_SCORE = "CamelLangchain4jEmbeddingStoreMinScore";

        /** Filter to apply to search operations for metadata-based filtering */
        @Metadata(description = "Search filter for metadata-based constraints",
                  javaType = "dev.langchain4j.store.embedding.filter.Filter")
        public static final String FILTER = "CamelLangchain4jEmbeddingStoreFilter";

    }
}
