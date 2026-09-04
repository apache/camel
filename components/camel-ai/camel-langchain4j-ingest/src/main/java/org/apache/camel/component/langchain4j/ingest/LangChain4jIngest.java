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
package org.apache.camel.component.langchain4j.ingest;

/**
 * Constants of the LangChain4j Ingest component.
 */
public final class LangChain4jIngest {

    /** Component URI scheme for ingest endpoints. */
    public static final String SCHEME = "langchain4j-ingest";

    /**
     * Exchange property carrying the resolved document id. When set, it wins over the header named by the
     * {@code documentIdHeader} endpoint option: a route that parses documents captures the id into this property before
     * the parse, so a document whose parser copies document metadata over the message headers cannot forge its own
     * identity.
     */
    public static final String DOCUMENT_ID_PROPERTY = "CamelLangChain4jIngestDocumentId";

    /**
     * Metadata key carrying the pipeline name on every written segment, so retrieval can tell which pipeline a match
     * came from.
     */
    public static final String METADATA_PIPELINE = "camel_ingest_pipeline";

    /**
     * Metadata key carrying the document id on every written segment: retrieval can cite it, and an engine that keeps a
     * store in step with a changing source needs it to find a document's vectors again.
     */
    public static final String METADATA_DOCUMENT_ID = "camel_ingest_document_id";

    private LangChain4jIngest() {
    }
}
