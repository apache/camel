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

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the LangChain4j Ingest component.
 */
@Configurer
@UriParams
public class LangChain4jIngestConfiguration implements Cloneable {

    @UriParam(description = "The EmbeddingStore to write segments to. When not set, the single bean of that type"
                            + " in the registry is used; zero or several beans fail the endpoint start with an"
                            + " error naming this option.")
    @Metadata(autowired = true)
    private EmbeddingStore<TextSegment> embeddingStore;

    @UriParam(description = "The EmbeddingModel to embed segments with. When not set, the single bean of that type"
                            + " in the registry is used; zero or several beans fail the endpoint start with an"
                            + " error naming this option.")
    @Metadata(autowired = true)
    private EmbeddingModel embeddingModel;

    @UriParam(description = "Maximum size of one segment, in characters.", defaultValue = "500")
    private int maxSegmentSize = 500;

    @UriParam(description = "How much of the previous segment each segment repeats, in characters. Overlap keeps a"
                            + " sentence split across a boundary retrievable from either side.",
              defaultValue = "50")
    private int maxOverlapSize = 50;

    @UriParam(description = "How many segments are embedded per request to the embedding model. Providers with"
                            + " generous per-request limits ingest large documents faster with a bigger batch; a"
                            + " batch carries at most embeddingBatchSize x maxSegmentSize characters, so tune the"
                            + " two together against the provider's token limits.",
              defaultValue = "32")
    private int embeddingBatchSize = 32;

    @UriParam(description = "Maximum size of one document in characters, applied to the text about to be split;"
                            + " 0, the default, means no limit. The pipeline holds a document in memory whole, so"
                            + " the cap is the protection against oversized - on a consumer-fed pipeline,"
                            + " attacker-sized - payloads. An oversized document fails the exchange cleanly and,"
                            + " with a repository configured, releases its dedup claim.",
              defaultValue = "0", label = "advanced")
    private int maxDocumentSize;

    @UriParam(description = "The DocumentSplitter deciding how a document becomes segments, referenced as #bean:name"
                            + " - LangChain4j ships alternatives beside the default recursive one. When set,"
                            + " maxSegmentSize and maxOverlapSize are ignored (they parameterize the default splitter"
                            + " only). Segments returned without the identity metadata are re-stamped, so a custom"
                            + " splitter cannot break citation. Not looked up by type on purpose - an application may"
                            + " hold unrelated splitters.",
              label = "advanced")
    private DocumentSplitter documentSplitter;

    @UriParam(description = "Name of the header carrying the document id, such as CamelAwsS3Key for an S3 consumer"
                            + " or CamelKafkaKey for a Kafka one. The CamelLangChain4jIngestDocumentId exchange"
                            + " property, when set, takes precedence - a route that parses documents captures the"
                            + " id into that property before the parse, so a document cannot forge its own"
                            + " identity. An exchange without an id fails.",
              defaultValue = LangChain4jIngestHeaders.DOCUMENT_ID)
    private String documentIdHeader = LangChain4jIngestHeaders.DOCUMENT_ID;

    @UriParam(description = "The IdempotentRepository remembering already ingested document ids, referenced as"
                            + " #bean:name. When set, a delivery whose id was already written is answered with a"
                            + " skipped result instead of being re-ingested: first write wins per id. A blank"
                            + " document releases its claim, so a later, populated delivery under the same id"
                            + " still ingests. The claim is eager: a duplicate racing an in-flight first delivery"
                            + " is answered skipped even if that delivery then fails - with an at-least-once source"
                            + " the skipped duplicate is acknowledged and the failed original may be the only other"
                            + " copy, so pair eager deduplication with a source that redelivers on failure. Not"
                            + " looked up by type on purpose - an application may hold unrelated idempotent"
                            + " repositories. The repository is started but never stopped by the endpoint (it may"
                            + " be shared); a persistent repository's lifecycle belongs to whoever created it.",
              label = "advanced")
    private IdempotentRepository idempotentRepository;

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    /**
     * Sets the embedding store to write to.
     */
    public void setEmbeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Sets the embedding model to embed with.
     */
    public void setEmbeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getMaxSegmentSize() {
        return maxSegmentSize;
    }

    /**
     * Sets the maximum size of one segment, in characters.
     */
    public void setMaxSegmentSize(int maxSegmentSize) {
        this.maxSegmentSize = maxSegmentSize;
    }

    public int getMaxOverlapSize() {
        return maxOverlapSize;
    }

    /**
     * Sets how much of the previous segment each segment repeats, in characters.
     */
    public void setMaxOverlapSize(int maxOverlapSize) {
        this.maxOverlapSize = maxOverlapSize;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    /**
     * Sets how many segments are embedded per request to the embedding model.
     */
    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public int getMaxDocumentSize() {
        return maxDocumentSize;
    }

    /**
     * Sets the maximum size of one document in characters; 0 means no limit.
     */
    public void setMaxDocumentSize(int maxDocumentSize) {
        this.maxDocumentSize = maxDocumentSize;
    }

    public DocumentSplitter getDocumentSplitter() {
        return documentSplitter;
    }

    /**
     * Sets the splitter deciding how a document becomes segments, replacing the default recursive one.
     */
    public void setDocumentSplitter(DocumentSplitter documentSplitter) {
        this.documentSplitter = documentSplitter;
    }

    public String getDocumentIdHeader() {
        return documentIdHeader;
    }

    /**
     * Sets the name of the header carrying the document id.
     */
    public void setDocumentIdHeader(String documentIdHeader) {
        this.documentIdHeader = documentIdHeader;
    }

    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    /**
     * Sets the repository remembering already ingested document ids.
     */
    public void setIdempotentRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    // ************************
    //
    // Clone
    //
    // ************************

    public LangChain4jIngestConfiguration copy() {
        try {
            return (LangChain4jIngestConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
