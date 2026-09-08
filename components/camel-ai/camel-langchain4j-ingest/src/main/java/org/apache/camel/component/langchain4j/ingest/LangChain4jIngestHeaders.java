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

import org.apache.camel.spi.Metadata;

/**
 * Headers the LangChain4j Ingest producer reads.
 */
public final class LangChain4jIngestHeaders {

    /**
     * The stable document id, when the {@code documentIdHeader} endpoint option does not name another header. Identity
     * is what update and delete semantics are built on, so a generated fallback would silently break replacement — an
     * absent id fails the exchange instead.
     *
     * <p>
     * The name follows the {@code Camel + ComponentName + Feature} convention of {@code design/headers.adoc}. The
     * camel-quarkus-langchain4j-ingest extension, whose engine this component originates from, released the header as
     * {@code CamelIngestDocumentId} (Camel Quarkus 3.39.0); that compatibility is honoured downstream — the extension
     * sets the {@code documentIdHeader} endpoint option to its legacy name when it delegates here. The same release
     * also stamped the segment metadata as {@code camel_quarkus_pipeline} and {@code camel_quarkus_document_id}, where
     * this component writes {@link LangChain4jIngest#METADATA_PIPELINE} and
     * {@link LangChain4jIngest#METADATA_DOCUMENT_ID} — recorded here so the whole compatibility story lives in one
     * place.
     */
    @Metadata(description = "The stable document id of the ingested payload, read when the documentIdHeader endpoint"
                            + " option does not name another header. The CamelLangChain4jIngestDocumentId exchange"
                            + " property, when set, takes precedence over any header.",
              javaType = "String")
    public static final String DOCUMENT_ID = "CamelLangChain4jIngestDocumentId";

    private LangChain4jIngestHeaders() {
    }
}
