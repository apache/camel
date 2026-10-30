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
package org.apache.camel.component.langchain4j.core.rag;

/**
 * Exchange property names set by {@link CamelContentRetriever} and segment metadata keys it reads and writes.
 * <p>
 * The metadata keys follow the {@code camel_<side>_} scheme of camel-langchain4j-ingest: {@code camel_ingest_*} keys
 * are written at ingestion time, {@code camel_retrieval_*} keys at retrieval time.
 */
public final class CamelContentRetrieverConstants {

    /** Exchange property: text of the original user message. Untrusted user text, never an identity. */
    public static final String QUERY_USER_MESSAGE = "CamelLangChain4jRetrieverUserMessage";
    /** Exchange property: chat memory id of the AI Service invocation. */
    public static final String QUERY_MEMORY_ID = "CamelLangChain4jRetrieverMemoryId";
    /** Exchange property: the AI Service invocation parameters, as a read-only {@code Map<String, Object>}. */
    public static final String QUERY_PARAMS = "CamelLangChain4jRetrieverParams";

    /** Segment metadata key: name of the source that produced the content. */
    public static final String METADATA_SOURCE = "camel_retrieval_source";
    /** Segment metadata key: id of the row or document the content comes from, for citation. */
    public static final String METADATA_DOCUMENT_ID = "camel_retrieval_document_id";
    /**
     * Segment metadata key written by camel-langchain4j-ingest ({@code LangChain4jIngest.METADATA_DOCUMENT_ID}): copied
     * to {@link #METADATA_DOCUMENT_ID} when the segment has no id of its own.
     */
    public static final String INGEST_METADATA_DOCUMENT_ID = "camel_ingest_document_id";

    private CamelContentRetrieverConstants() {
    }
}
