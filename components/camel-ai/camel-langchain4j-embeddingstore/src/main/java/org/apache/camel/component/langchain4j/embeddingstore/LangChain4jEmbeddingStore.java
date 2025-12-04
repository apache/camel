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

    private LangChain4jEmbeddingStore() {}
}
