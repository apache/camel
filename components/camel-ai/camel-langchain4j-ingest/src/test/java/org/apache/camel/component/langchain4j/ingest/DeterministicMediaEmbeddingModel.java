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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;

/**
 * Deterministic fake that also accepts media: same bytes, same vector. Records the content and MIME types it was
 * handed.
 */
class DeterministicMediaEmbeddingModel extends DeterministicEmbeddingModel {

    private final Set<ContentType> supported;
    private final List<String> mimeTypes = new CopyOnWriteArrayList<>();
    private final List<ContentType> contentTypes = new CopyOnWriteArrayList<>();

    /** Supports every medium LangChain4j knows. */
    DeterministicMediaEmbeddingModel(int dimension) {
        this(dimension, Set.of(ContentType.TEXT, ContentType.IMAGE, ContentType.AUDIO, ContentType.VIDEO,
                ContentType.PDF));
    }

    /** Supports the given content types only, for the tests of what a narrower model is refused. */
    DeterministicMediaEmbeddingModel(int dimension, Set<ContentType> supported) {
        super(dimension);
        this.supported = supported;
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return supported;
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>(request.inputs().size());
        for (EmbeddingInput input : request.inputs()) {
            Content media = mediaOf(input);
            if (media == null) {
                embeddings.add(embedding(input.text()));
            } else {
                contentTypes.add(media.type());
                mimeTypes.add(mimeTypeOf(media));
                embeddings.add(embedding(base64Of(media)));
            }
        }
        return EmbeddingResponse.builder().embeddings(embeddings).build();
    }

    /** The vector this fake produces for the given bytes: what a query by the same file embeds to. */
    Embedding embeddingOf(byte[] bytes) {
        return embedding(Base64.getEncoder().encodeToString(bytes));
    }

    /** MIME types handed to the model, in call order. */
    List<String> mimeTypes() {
        return mimeTypes;
    }

    /** Content types handed to the model, in call order. */
    List<ContentType> contentTypes() {
        return contentTypes;
    }

    private static Content mediaOf(EmbeddingInput input) {
        if (input.contents() == null) {
            return null;
        }
        for (Content content : input.contents()) {
            if (content.type() != ContentType.TEXT) {
                return content;
            }
        }
        return null;
    }

    private static String mimeTypeOf(Content content) {
        return switch (content.type()) {
            case AUDIO -> ((AudioContent) content).audio().mimeType();
            case IMAGE -> ((ImageContent) content).image().mimeType();
            case VIDEO -> ((VideoContent) content).video().mimeType();
            case PDF -> ((PdfFileContent) content).pdfFile().mimeType();
            case TEXT -> throw new IllegalArgumentException("not media: " + content);
        };
    }

    private static String base64Of(Content content) {
        return switch (content.type()) {
            case AUDIO -> ((AudioContent) content).audio().base64Data();
            case IMAGE -> ((ImageContent) content).image().base64Data();
            case VIDEO -> ((VideoContent) content).video().base64Data();
            case PDF -> ((PdfFileContent) content).pdfFile().base64Data();
            case TEXT -> throw new IllegalArgumentException("not media: " + content);
        };
    }
}
