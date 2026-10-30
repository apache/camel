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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.core.LangChain4j;
import org.apache.camel.util.IOHelper;

import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.INGEST_METADATA_DOCUMENT_ID;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.METADATA_DOCUMENT_ID;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.METADATA_SOURCE;

/**
 * Converts the message body returned by a retrieval route to {@code List<Content>}, one {@link Content} per element:
 * <ul>
 * <li>{@code null}, empty collection: empty list</li>
 * <li>{@link Content}, {@link TextSegment}, {@link String}: used as-is</li>
 * <li>{@link EmbeddingMatch}: its embedded item, with the score and the embedding id as {@link ContentMetadata}</li>
 * <li>{@link Map}: one {@code key: value} line per entry</li>
 * <li>anything else: converted to {@link String} with the Camel type converter</li>
 * </ul>
 * A {@link Collection}, an object array, an {@link Iterator} or a {@link Stream} body is split into its elements; any
 * other body is a single element. Reading stops once {@code maxResults} items or {@code maxChars} characters are
 * converted, and a body holding a resource (a {@link Stream}, a {@link java.io.Closeable} iterator) is then closed.
 * Blank elements are skipped. Every segment gets {@value CamelContentRetrieverConstants#METADATA_SOURCE} and the
 * response headers prefixed with {@value LangChain4j#METADATA_PREFIX}. The reserved {@code camel_retrieval_*} and
 * {@code camel_ingest_*} keys never come from response headers.
 * {@value CamelContentRetrieverConstants#METADATA_DOCUMENT_ID} comes from the first of: the {@code documentIdKey} entry
 * of a {@link Map} or metadata key of a segment, the key already set on the segment, or its
 * {@value CamelContentRetrieverConstants#INGEST_METADATA_DOCUMENT_ID}, written by camel-langchain4j-ingest.
 * <p>
 * {@code maxResults} keeps the first N items, {@code maxChars} truncates the combined text. {@code 0} means unlimited,
 * as {@code maxDocumentSize} in camel-langchain4j-ingest.
 */
final class ContentConverter {

    private static final String RESERVED_RETRIEVAL_PREFIX = "camel_retrieval_";
    private static final String RESERVED_INGEST_PREFIX = "camel_ingest_";

    private ContentConverter() {
    }

    static List<Content> toContents(
            Exchange exchange, String sourceName, String documentIdKey, int maxResults, int maxChars) {
        Object body = exchange.getMessage().getBody();
        // reading may stop early: a body holding a resource (Files.lines, the Scanner of tokenize) is closed once
        // converted, read in full or not
        List<Content> contents;
        try {
            Iterator<?> items = body instanceof Stream<?> stream ? stream.iterator() : items(body);
            contents = toContents(exchange, items, sourceName, documentIdKey, maxResults, maxChars);
        } catch (RuntimeException e) {
            discard(exchange);
            throw e;
        }
        close(body);
        return contents;
    }

    /** Releases what the body of a response that is never converted holds, such as an answer after a timeout. */
    static void discard(Exchange exchange) {
        try {
            close(exchange.getMessage().getBody());
        } catch (RuntimeException e) {
            // nothing reads this answer: a failing close must not fail anything else
        }
    }

    private static void close(Object body) {
        if (body instanceof Stream<?> stream) {
            stream.close();
            return;
        }
        try {
            // a Closeable iterator, as the splitter closes it; rethrows the read error a Scanner swallowed
            IOHelper.closeIterator(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Content> toContents(
            Exchange exchange, Iterator<?> items, String sourceName, String documentIdKey, int maxResults,
            int maxChars) {
        if (!items.hasNext()) {
            return Collections.emptyList();
        }
        Map<String, Object> headerMetadata = headerMetadata(exchange);
        List<Content> contents = new ArrayList<>();
        // stop once maxResults items or maxChars characters are read: the rest of an iterator or a stream is never
        // pulled, not even one element to look ahead
        int chars = 0;
        while ((maxResults == 0 || contents.size() < maxResults) && (maxChars == 0 || chars < maxChars)
                && items.hasNext()) {
            Content content = toContent(exchange, items.next(), sourceName, documentIdKey, headerMetadata);
            if (content != null) {
                contents.add(content);
                chars += content.textSegment().text().length();
            }
        }
        if (maxChars > 0) {
            contents = truncate(contents, maxChars);
        }
        return contents;
    }

    private static Iterator<?> items(Object body) {
        if (body == null) {
            return Collections.emptyIterator();
        }
        if (body instanceof Collection<?> collection) {
            return collection.iterator();
        }
        if (body instanceof Object[] array) {
            return Arrays.asList(array).iterator();
        }
        if (body instanceof Iterator<?> iterator) {
            return iterator;
        }
        return Collections.singletonList(body).iterator();
    }

    private static Content toContent(
            Exchange exchange, Object item, String sourceName, String documentIdKey, Map<String, Object> headerMetadata) {
        if (item == null) {
            return null;
        }
        Map<ContentMetadata, Object> contentMetadata = null;
        if (item instanceof EmbeddingMatch<?> match) {
            // the default output of an embedding store search: the vector itself never reaches the prompt
            contentMetadata = new EnumMap<>(ContentMetadata.class);
            if (match.score() != null) {
                contentMetadata.put(ContentMetadata.SCORE, match.score());
            }
            if (match.embeddingId() != null) {
                contentMetadata.put(ContentMetadata.EMBEDDING_ID, match.embeddingId());
            }
            item = match.embedded();
            if (item == null) {
                return null;
            }
        }
        TextSegment segment;
        Object documentId = null;
        if (item instanceof Content content) {
            if (contentMetadata == null) {
                contentMetadata = content.metadata();
            } else {
                // an EmbeddingMatch<Content>: the score and the id of this match win over the stored ones
                Map<ContentMetadata, Object> merged = new EnumMap<>(ContentMetadata.class);
                merged.putAll(content.metadata());
                merged.putAll(contentMetadata);
                contentMetadata = merged;
            }
            segment = content.textSegment();
        } else if (item instanceof TextSegment textSegment) {
            segment = textSegment;
        } else if (item instanceof Map<?, ?> map) {
            if (documentIdKey != null) {
                documentId = map.get(documentIdKey);
            }
            segment = textSegment(toText(map), null);
        } else {
            String text = item instanceof String s
                    ? s : exchange.getContext().getTypeConverter().convertTo(String.class, exchange, item);
            segment = textSegment(text, null);
        }
        if (segment == null) {
            return null;
        }
        if (documentId == null && documentIdKey != null) {
            // a TextSegment or Content element, an embedding store hit for example: the key is a metadata key
            documentId = segment.metadata().toMap().get(documentIdKey);
        }

        Map<String, Object> metadata = new HashMap<>(headerMetadata);
        metadata.putAll(segment.metadata().toMap());
        // the source is always ours; a segment cannot spoof it
        metadata.put(METADATA_SOURCE, sourceName);
        if (documentId != null) {
            metadata.put(METADATA_DOCUMENT_ID, toMetadataValue(documentId));
        } else if (!metadata.containsKey(METADATA_DOCUMENT_ID) && metadata.containsKey(INGEST_METADATA_DOCUMENT_ID)) {
            // an ingested segment: cite it by its ingest id. Only segment metadata can hold it, headers are filtered
            metadata.put(METADATA_DOCUMENT_ID, metadata.get(INGEST_METADATA_DOCUMENT_ID));
        }
        TextSegment enriched = TextSegment.from(segment.text(), Metadata.from(metadata));
        return contentMetadata != null ? Content.from(enriched, contentMetadata) : Content.from(enriched);
    }

    private static TextSegment textSegment(String text, Metadata metadata) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return metadata != null ? TextSegment.from(text, metadata) : TextSegment.from(text);
    }

    private static List<Content> truncate(List<Content> contents, int maxChars) {
        List<Content> answer = new ArrayList<>(contents.size());
        int remaining = maxChars;
        for (Content content : contents) {
            if (remaining <= 0) {
                break;
            }
            String text = content.textSegment().text();
            if (text.length() <= remaining) {
                answer.add(content);
                remaining -= text.length();
            } else {
                // never split a surrogate pair
                int end = Character.isHighSurrogate(text.charAt(remaining - 1)) ? remaining - 1 : remaining;
                String truncated = text.substring(0, end);
                if (!truncated.isBlank()) {
                    TextSegment segment = content.textSegment();
                    answer.add(Content.from(TextSegment.from(truncated, segment.metadata()), content.metadata()));
                }
                remaining = 0;
            }
        }
        return answer;
    }

    static String toText(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, Object> headerMetadata(Exchange exchange) {
        Map<String, Object> metadata = new HashMap<>();
        for (Map.Entry<String, Object> header : exchange.getMessage().getHeaders().entrySet()) {
            if (header.getKey().startsWith(LangChain4j.METADATA_PREFIX) && header.getValue() != null) {
                String key = header.getKey().substring(LangChain4j.METADATA_PREFIX_LEN);
                // LangChain4j metadata rejects a blank key
                if (key.isBlank()) {
                    continue;
                }
                // the reserved keys are always ours: a response header cannot spoof them. Segment metadata may set
                // the document id on purpose (a route mapping its own id), so only headers are filtered.
                if (key.startsWith(RESERVED_RETRIEVAL_PREFIX) || key.startsWith(RESERVED_INGEST_PREFIX)) {
                    continue;
                }
                metadata.put(key, toMetadataValue(header.getValue()));
            }
        }
        return metadata;
    }

    // LangChain4j Metadata only accepts String, UUID, Integer, Long, Float, Double
    private static Object toMetadataValue(Object value) {
        if (value instanceof String || value instanceof UUID || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double) {
            return value;
        }
        return String.valueOf(value);
    }
}
