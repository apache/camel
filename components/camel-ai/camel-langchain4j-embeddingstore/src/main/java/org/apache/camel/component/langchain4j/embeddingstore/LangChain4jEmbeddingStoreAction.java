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
 * Enumeration of supported embedding store operations.
 *
 * <p>
 * Each action represents a different type of operation that can be performed on the embedding store through the Camel
 * component.
 * </p>
 */
public enum LangChain4jEmbeddingStoreAction {
    /** Add an embedding to the store with optional text segment and metadata */
    ADD,

    /** Remove an embedding from the store by id */
    REMOVE,

    /** Perform similarity search to find embeddings */
    SEARCH
}
