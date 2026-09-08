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

import java.util.Set;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Splits the message body into segments, embeds them in batches and writes them to the embedding store; the message
 * body is replaced with the {@link IngestResult}.
 *
 * <p>
 * With an {@code idempotentRepository} configured, the producer claims the document id before writing: a duplicate
 * delivery is answered {@code SKIPPED} without touching the store, a blank document releases its claim so a later,
 * populated delivery under the same id still ingests, and a failed write releases it so the delivery can be retried.
 * The eager claim has a documented consequence: a duplicate racing an in-flight first delivery is answered
 * {@code SKIPPED} even if that delivery then fails and releases the id — with an at-least-once source the skipped
 * duplicate is acknowledged, so the failed original must be redelivered by its own source or the document is in neither
 * the store nor a queue. Claim-on-success semantics are the eventual alternative for sources that cannot redeliver.
 */
public class LangChain4jIngestProducer extends DefaultProducer {

    private final LangChain4jIngestEndpoint endpoint;
    private final LangChain4jIngestConfiguration configuration;
    private IngestService service;

    public LangChain4jIngestProducer(LangChain4jIngestEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String pipeline = endpoint.getPipelineName();
        if (pipeline == null || pipeline.isBlank()) {
            throw new IllegalArgumentException(
                    "The ingestion pipeline name is missing. Use langchain4j-ingest:pipelineName in the endpoint URI.");
        }
        if (configuration.getDocumentIdHeader() == null || configuration.getDocumentIdHeader().isBlank()) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': documentIdHeader must not be blank. Omit the option for"
                                               + " the default, or name the header carrying the document id.");
        }
        // the splitter bounds parameterize the default recursive splitter only: with a custom
        // documentSplitter they are documented as ignored, so they are not validated either
        if (configuration.getDocumentSplitter() == null
                && (configuration.getMaxSegmentSize() <= 0 || configuration.getMaxOverlapSize() < 0
                        || configuration.getMaxOverlapSize() >= configuration.getMaxSegmentSize())) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': maxSegmentSize must be positive and maxOverlapSize must"
                                               + " be non-negative and smaller than it (got "
                                               + configuration.getMaxSegmentSize()
                                               + " / " + configuration.getMaxOverlapSize() + ")");
        }

        if (configuration.getEmbeddingBatchSize() < 1) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': embeddingBatchSize must be positive (got "
                                               + configuration.getEmbeddingBatchSize() + ")");
        }

        if (configuration.getMaxDocumentSize() < 0) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': maxDocumentSize must not be negative (got "
                                               + configuration.getMaxDocumentSize() + ")");
        }

        EmbeddingStore<TextSegment> store
                = resolve(EmbeddingStore.class, configuration.getEmbeddingStore(), "embedding store", "embeddingStore");
        EmbeddingModel model
                = resolve(EmbeddingModel.class, configuration.getEmbeddingModel(), "embedding model", "embeddingModel");
        service = configuration.getDocumentSplitter() != null
                ? new IngestService(
                        pipeline, store, model, configuration.getDocumentSplitter(),
                        configuration.getEmbeddingBatchSize(), configuration.getMaxDocumentSize())
                : new IngestService(
                        pipeline, store, model, configuration.getMaxSegmentSize(),
                        configuration.getMaxOverlapSize(), configuration.getEmbeddingBatchSize(),
                        configuration.getMaxDocumentSize());

        IdempotentRepository repository = configuration.getIdempotentRepository();
        if (repository != null) {
            // a repository bound via configuration does not pass through the registry's bind
            // hook, so a CamelContextAware implementation would otherwise run contextless. It is
            // started but never stopped here: the bean may be shared, and stopping the in-memory
            // repository would clear it
            CamelContextAware.trySetCamelContext(repository, getEndpoint().getCamelContext());
            ServiceHelper.startService(repository);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String documentId = resolveDocumentId(exchange);

        IdempotentRepository repository = configuration.getIdempotentRepository();
        if (repository == null) {
            exchange.getMessage().setBody(service.ingest(documentId, exchange.getMessage().getBody(String.class)));
            return;
        }

        // the claim is eager: a duplicate racing an in-flight first delivery is answered SKIPPED
        // even if that delivery then fails and releases the id. The claim also precedes the body
        // read, so a skipped duplicate never pays for materialising a large payload. The
        // Exchange-aware repository overloads are used throughout, matching the idempotent
        // consumer EIP - a repository overriding only those variants is not bypassed
        if (!repository.add(exchange, documentId)) {
            exchange.getMessage().setBody(
                    new IngestResult(service.pipeline(), documentId, 0, IngestResult.Outcome.SKIPPED));
            return;
        }
        IngestResult result;
        try {
            result = service.ingest(documentId, exchange.getMessage().getBody(String.class));
        } catch (Exception e) {
            // a failed write must not keep the claim, or the delivery could never be retried.
            // An Error (an OutOfMemoryError, say) is deliberately not caught: under a VM-level
            // failure the release itself could not be trusted, so the id stays claimed and later
            // deliveries are answered SKIPPED - clear it from the repository to re-ingest
            try {
                repository.remove(exchange, documentId);
            } catch (Exception rollback) {
                // the release often fails from the same root cause; the original failure is
                // the one worth reporting
                e.addSuppressed(rollback);
            }
            throw e;
        }
        if (result.outcome() == IngestResult.Outcome.EMPTY) {
            // a blank document wrote nothing, so it must not keep the claim - a later, populated
            // delivery under the same id would be answered SKIPPED
            repository.remove(exchange, documentId);
        } else {
            repository.confirm(exchange, documentId);
        }
        exchange.getMessage().setBody(result);
    }

    /**
     * The exchange property wins over the header: a route that parses documents captures the id into the property
     * before the parse, and a parser (Tika) copies document metadata over the headers, so a header read here could be
     * spoofed by the document itself. A property that is present but blank fails the exchange on purpose, without
     * falling back to the header: the route deliberately captured the id, so a blank capture is a broken expression to
     * surface loudly, not a case to paper over with a value of weaker provenance.
     */
    private String resolveDocumentId(Exchange exchange) {
        String documentId = exchange.getProperty(LangChain4jIngest.DOCUMENT_ID_PROPERTY, String.class);
        if (documentId == null) {
            documentId = exchange.getMessage().getHeader(configuration.getDocumentIdHeader(), String.class);
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + endpoint.getPipelineName() + "': no document id. Set the "
                                               + configuration.getDocumentIdHeader()
                                               + " header (or the " + LangChain4jIngest.DOCUMENT_ID_PROPERTY
                                               + " exchange property), or point the documentIdHeader endpoint option"
                                               + " at where the consumer puts it.");
        }
        return documentId;
    }

    /**
     * Resolves a configured bean, or the single registry bean of the type. Picking one of several silently would bind
     * the pipeline to whichever bean happened to be found first, so zero and several candidates each fail with the fix
     * in the message. Runs after autowiring: with exactly one candidate the configuration already carries it.
     */
    // Class<? super T> ties the class literal to T at both call sites without any cast there:
    // EmbeddingStore.class satisfies the bound because EmbeddingStore<TextSegment> is a
    // subtype of the raw class, while a swapped literal fails to compile. The registry only
    // returns instances of the given type, so the one cast below cannot fail
    @SuppressWarnings("unchecked")
    private <T> T resolve(Class<? super T> type, T configured, String what, String option) {
        if (configured != null) {
            return configured;
        }
        Set<?> candidates = getEndpoint().getCamelContext().getRegistry().findByType(type);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + endpoint.getPipelineName() + "' needs an " + what + ", but no bean of"
                                               + " that type exists. Bind one to the registry, or set the " + option
                                               + " endpoint option.");
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + endpoint.getPipelineName() + "' found " + candidates.size() + " " + what
                                               + " beans. Name the one to use with the " + option
                                               + "=#bean:name endpoint option.");
        }
        return (T) candidates.iterator().next();
    }
}
