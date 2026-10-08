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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Predicate;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.AntPathMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Splits the message body into segments, embeds them in batches and writes them to the embedding store; the message
 * body is replaced with the {@link IngestResult}. With {@link IngestModality#MEDIA} the body is embedded whole instead.
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

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jIngestProducer.class);

    private final LangChain4jIngestEndpoint endpoint;
    private final LangChain4jIngestConfiguration configuration;
    /** One of the two is set at start, by modality. */
    private IngestService textService;
    private MediaIngestService mediaService;
    private String[] includeIds;
    private String[] excludeIds;
    private boolean media;
    /** The configured contentType, normalised; null when unset. Content-type headers are ignored on purpose. */
    private String contentType;
    /** What the model declares it can embed; media mode only. */
    private Set<ContentType> supportedTypes;

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
        media = configuration.getModality() == IngestModality.MEDIA;
        contentType = normalizeContentType(configuration.getContentType());
        if (!media && contentType != null) {
            // silently ignoring it would embed media bytes as text on a forgotten modality=media
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': contentType only applies to modality=media (got '"
                                               + configuration.getContentType() + "'). Set modality=media, or"
                                               + " remove the option.");
        }
        if (media && configuration.getDocumentSplitter() != null) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': documentSplitter does not apply to modality=media, where"
                                               + " a document is embedded whole. Remove the option.");
        }
        // the splitter bounds parameterize the default recursive splitter only: with a custom
        // documentSplitter, or in media mode, they are documented as ignored, so they are not
        // validated either
        if (!media && configuration.getDocumentSplitter() == null
                && (configuration.getMaxSegmentSize() <= 0 || configuration.getMaxOverlapSize() < 0
                        || configuration.getMaxOverlapSize() >= configuration.getMaxSegmentSize())) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': maxSegmentSize must be positive and maxOverlapSize must"
                                               + " be non-negative and smaller than it (got "
                                               + configuration.getMaxSegmentSize()
                                               + " / " + configuration.getMaxOverlapSize() + ")");
        }

        // like the splitter bounds, the batch size does not apply in media mode, so it is not
        // validated there either
        if (!media && configuration.getEmbeddingBatchSize() < 1) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': embeddingBatchSize must be positive (got "
                                               + configuration.getEmbeddingBatchSize() + ")");
        }

        if (configuration.getMaxDocumentSize() < 0) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': maxDocumentSize must not be negative (got "
                                               + configuration.getMaxDocumentSize() + ")");
        }

        if (configuration.getMinDocumentSize() < 0) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': minDocumentSize must not be negative (got "
                                               + configuration.getMinDocumentSize() + ")");
        }
        if (configuration.getMaxDocumentSize() > 0
                && configuration.getMinDocumentSize() > configuration.getMaxDocumentSize()) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + pipeline + "': minDocumentSize must not exceed maxDocumentSize (got "
                                               + configuration.getMinDocumentSize() + " > "
                                               + configuration.getMaxDocumentSize() + ")");
        }

        includeIds = parsePatterns(configuration.getIncludeId());
        excludeIds = parsePatterns(configuration.getExcludeId());
        if (configuration.getDocumentFilter() != null) {
            // initialised and started, but - like the repository below - never stopped: the
            // bean may be shared, and stopping it here would tear it down under other users
            configuration.getDocumentFilter().initPredicate(getEndpoint().getCamelContext());
            ServiceHelper.startService(configuration.getDocumentFilter());
        }

        EmbeddingStore<TextSegment> store
                = resolve(EmbeddingStore.class, configuration.getEmbeddingStore(), "embedding store", "embeddingStore");
        EmbeddingModel model
                = resolve(EmbeddingModel.class, configuration.getEmbeddingModel(), "embedding model", "embeddingModel");
        if (media) {
            // LangChain4j would reject the first request anyway, but only once a document has
            // been read; the misconfiguration is better reported before any is. allMatch is
            // vacuously true for an empty set, which declares no medium either
            supportedTypes = model.supportedContentTypes();
            if (supportedTypes == null || supportedTypes.stream().allMatch(type -> type == ContentType.TEXT)) {
                throw new IllegalArgumentException(
                        "Ingestion pipeline '" + pipeline + "': modality=media, but the embedding model "
                                                   + model.getClass().getName() + " does not support any media input"
                                                   + " (its supportedContentTypes() are " + supportedTypes
                                                   + "). Configure an EmbeddingModel that declares AUDIO, IMAGE,"
                                                   + " VIDEO or PDF.");
            }
            // with listeners attached, LangChain4j 1.20 builds the listener context from the
            // input text, which media lacks, so every media request would fail inside the
            // library with an opaque "text cannot be null or blank". addListener wraps the model
            // in ListeningEmbeddingModel, a package-private class that does not expose its
            // listeners, hence the check by name beside the listeners() one
            // TODO CAMEL-25332: drop the class-name check once a LangChain4j release after 1.20 returns the
            // wrapped listeners from listeners() or builds the context without text
            if ("dev.langchain4j.model.embedding.ListeningEmbeddingModel".equals(model.getClass().getName())
                    || (model.listeners() != null && !model.listeners().isEmpty())) {
                throw new IllegalArgumentException(
                        "Ingestion pipeline '" + pipeline + "': modality=media, but the embedding model has"
                                                   + " EmbeddingModelListeners attached. LangChain4j builds the"
                                                   + " listener context from the input text, which media lacks, so"
                                                   + " every request would fail; use a model without listeners.");
            }
            if (contentType != null) {
                // one type for every document: a medium the model lacks fails here, not per document
                requireSupported(contentType, "the contentType option");
            }
            mediaService = new MediaIngestService(
                    pipeline, store, model, configuration.getMaxDocumentSize(), configuration.getMinDocumentSize());
            LOG.debug("Ingestion pipeline '{}': modality=media, maxDocumentSize={} and minDocumentSize={} count bytes",
                    pipeline, configuration.getMaxDocumentSize(), configuration.getMinDocumentSize());
        } else if (configuration.getDocumentSplitter() != null) {
            textService = new IngestService(
                    pipeline, store, model, configuration.getDocumentSplitter(),
                    configuration.getEmbeddingBatchSize(), configuration.getMaxDocumentSize(),
                    configuration.getMinDocumentSize());
        } else {
            textService = new IngestService(
                    pipeline, store, model, configuration.getMaxSegmentSize(),
                    configuration.getMaxOverlapSize(), configuration.getEmbeddingBatchSize(),
                    configuration.getMaxDocumentSize(), configuration.getMinDocumentSize());
        }

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

        // before the claim and the body read: a filtered document never occupies its id and
        // never pays for materialising the payload
        if (!idAccepted(documentId)) {
            exchange.getMessage().setBody(filtered(documentId));
            return;
        }
        // the media type depends on the id alone, so it is resolved here as well: a document
        // whose type cannot be told, or whose medium the model lacks, never occupies its id and
        // never pays for the payload
        String mimeType = null;
        ContentType medium = null;
        if (media) {
            mimeType = mediaTypeOf(documentId);
            medium = requireSupported(mimeType, "document '" + documentId + "'");
        }

        IdempotentRepository repository = configuration.getIdempotentRepository();
        if (repository == null) {
            exchange.getMessage().setBody(ingestUnlessFiltered(exchange, documentId, mimeType, medium));
            return;
        }

        // the claim is eager: a duplicate racing an in-flight first delivery is answered SKIPPED
        // even if that delivery then fails and releases the id. The claim also precedes the body
        // read, so a skipped duplicate never pays for materialising a large payload. The
        // Exchange-aware repository overloads are used throughout, matching the idempotent
        // consumer EIP - a repository overriding only those variants is not bypassed
        if (!repository.add(exchange, documentId)) {
            exchange.getMessage().setBody(
                    new IngestResult(endpoint.getPipelineName(), documentId, 0, IngestResult.Outcome.SKIPPED));
            return;
        }
        IngestResult result;
        try {
            result = ingestUnlessFiltered(exchange, documentId, mimeType, medium);
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
        if (result.outcome() == IngestResult.Outcome.EMPTY || result.outcome() == IngestResult.Outcome.FILTERED) {
            // a blank or filtered document wrote nothing, so it must not keep the claim - a
            // later delivery under the same id would be answered SKIPPED
            repository.remove(exchange, documentId);
        } else {
            repository.confirm(exchange, documentId);
        }
        exchange.getMessage().setBody(result);
    }

    /**
     * The content filters, in claim scope: the documentFilter predicate sees the body, and minDocumentSize (inside
     * {@link IngestService}) needs the text - both run after the dedup claim, unlike the id patterns.
     */
    private IngestResult ingestUnlessFiltered(Exchange exchange, String documentId, String mimeType, ContentType medium)
            throws InvalidPayloadException, IOException {
        Predicate filter = configuration.getDocumentFilter();
        if (filter != null && !filter.matches(exchange)) {
            return filtered(documentId);
        }
        if (media) {
            // a file consumer announces the size up front: an oversized file is refused before it is read
            Long announced = exchange.getMessage().getHeader(Exchange.FILE_LENGTH, Long.class);
            if (announced != null) {
                mediaService.checkSize(documentId, announced);
            }
            return mediaService.ingest(documentId, mediaBody(exchange), mimeType, medium);
        }
        return textService.ingest(documentId, textBody(exchange));
    }

    /**
     * The media body as bytes, not a String - a charset conversion would corrupt them. A null body stays EMPTY;
     * anything else must convert, so a wrong body type (a POJO, say) fails as an InvalidPayloadException instead of
     * being answered EMPTY by a silent null conversion. With maxDocumentSize set, a body not yet in memory is read only
     * one byte past the cap, enough for the size check to refuse it.
     */
    private byte[] mediaBody(Exchange exchange) throws InvalidPayloadException, IOException {
        Object body = exchange.getMessage().getBody();
        int limit = readLimit();
        InputStream stream = body == null || body instanceof byte[] || limit < 0
                ? null : exchange.getMessage().getBody(InputStream.class);
        if (stream == null) {
            return body == null ? null : exchange.getMessage().getMandatoryBody(byte[].class);
        }
        try (stream) {
            return stream.readNBytes(limit);
        }
    }

    /**
     * The text body. With maxDocumentSize set, a body not yet a String is read only one character past the cap, enough
     * for the size check to refuse it.
     */
    private String textBody(Exchange exchange) throws IOException {
        Object body = exchange.getMessage().getBody();
        int limit = readLimit();
        Reader reader = body == null || body instanceof String || limit < 0
                ? null : readerOf(exchange);
        if (reader == null) {
            return exchange.getMessage().getBody(String.class);
        }
        try (reader) {
            StringBuilder text = new StringBuilder();
            char[] chunk = new char[8192];
            int read;
            while (text.length() < limit
                    && (read = reader.read(chunk, 0, Math.min(chunk.length, limit - text.length()))) != -1) {
                text.append(chunk, 0, read);
            }
            return text.toString();
        }
    }

    /** The body as a Reader, or null; a File or a Path converts to a BufferedReader, not to a Reader. */
    private static Reader readerOf(Exchange exchange) {
        Reader reader = exchange.getMessage().getBody(Reader.class);
        return reader != null ? reader : exchange.getMessage().getBody(BufferedReader.class);
    }

    /** How far a body is read: one unit past maxDocumentSize, so an oversized one shows; -1 without a cap. */
    private int readLimit() {
        int max = configuration.getMaxDocumentSize();
        return max <= 0 ? -1 : max == Integer.MAX_VALUE ? max : max + 1;
    }

    private IngestResult filtered(String documentId) {
        // the endpoint's pipeline name, not service.pipeline(): same value, but valid even
        // if a harness invokes the producer before doStart
        return new IngestResult(endpoint.getPipelineName(), documentId, 0, IngestResult.Outcome.FILTERED);
    }

    /** The configured contentType, else the type the document id's file extension implies. */
    private String mediaTypeOf(String documentId) {
        if (contentType != null) {
            return contentType;
        }
        String type = MediaTypes.fromDocumentId(documentId);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + endpoint.getPipelineName() + "': cannot tell the media type of document '"
                                               + documentId + "' from its extension. Set the contentType endpoint"
                                               + " option.");
        }
        return type;
    }

    /**
     * The LangChain4j content type a MIME type belongs to, checked against what the model declares so a medium it
     * cannot embed is refused before the body is read; fails naming what needed the type.
     */
    private ContentType requireSupported(String mimeType, String what) {
        ContentType type = MediaTypes.contentTypeOf(mimeType);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + endpoint.getPipelineName() + "': '" + mimeType + "' (" + what
                                               + ") is not a media type this pipeline can embed: audio/*, image/*,"
                                               + " video/* or application/pdf.");
        }
        if (!supportedTypes.contains(type)) {
            throw new IllegalArgumentException(
                    "Ingestion pipeline '" + endpoint.getPipelineName() + "': the embedding model does not support "
                                               + type + " input, needed for " + what + " (its supportedContentTypes()"
                                               + " are " + supportedTypes + ").");
        }
        return type;
    }

    /**
     * Trimmed, lower-cased (RFC 2045 types are case-insensitive) and without parameters, the form an embedding provider
     * expects: {@code Audio/WAV; rate=16000} is handed to the model as {@code audio/wav}. Blank means unset.
     */
    private static String normalizeContentType(String value) {
        if (value == null) {
            return null;
        }
        int semicolon = value.indexOf(';');
        String type = (semicolon < 0 ? value : value.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
        return type.isEmpty() ? null : type;
    }

    /** Exclusion wins over inclusion; with includeId set, only matching ids pass. */
    private boolean idAccepted(String documentId) {
        if (AntPathMatcher.INSTANCE.anyMatch(excludeIds, documentId)) {
            return false;
        }
        return includeIds == null || AntPathMatcher.INSTANCE.anyMatch(includeIds, documentId);
    }

    // normalised to null when no pattern remains: an all-blank includeId must accept
    // everything, while an empty array handed to anyMatch would reject everything
    private static String[] parsePatterns(String patterns) {
        if (patterns == null || patterns.isBlank()) {
            return null;
        }
        String[] parsed = Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toArray(String[]::new);
        return parsed.length == 0 ? null : parsed;
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
