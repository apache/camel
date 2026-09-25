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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The case the single {@code media} value exists for: one folder holding several media, one file consumer, one store.
 * The bodies are fake except the WAV, see {@link LangChain4jIngestMediaTest}: the medium comes from the file name.
 */
class LangChain4jIngestMediaFolderTest extends CamelTestSupport {

    @TempDir
    Path folder;

    @BindToRegistry("allStore")
    private final InMemoryEmbeddingStore<TextSegment> allStore = new InMemoryEmbeddingStore<>();

    @BindToRegistry("allModel")
    private final DeterministicMediaEmbeddingModel allModel = new DeterministicMediaEmbeddingModel(16);

    @BindToRegistry("imageStore")
    private final InMemoryEmbeddingStore<TextSegment> imageStore = new InMemoryEmbeddingStore<>();

    @BindToRegistry("imageModel")
    private final DeterministicMediaEmbeddingModel imageModel
            = new DeterministicMediaEmbeddingModel(16, Set.of(ContentType.IMAGE));

    /** File name to error message of the deliveries the image-only pipeline refused. */
    private final Map<String, String> refusals = new ConcurrentHashMap<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a knowledge base reads its source: noop leaves the files where they are, and no
                // charset is set, so the bytes reach the endpoint untouched
                from("file:" + folder.resolve("gallery") + "?noop=true&initialDelay=0&delay=200")
                        .routeId("gallery")
                        .to("langchain4j-ingest:gallery?modality=media&documentIdHeader=CamelFileName"
                            + "&embeddingStore=#bean:allStore&embeddingModel=#bean:allModel");

                from("file:" + folder.resolve("photos") + "?noop=true&initialDelay=0&delay=200")
                        .routeId("photos")
                        // recorded and marked handled, so a refused file is not retried on every
                        // poll while the test runs; without the handler it would be, loudly
                        .onException(IllegalArgumentException.class).handled(true)
                        .process(exchange -> refusals.put(
                                exchange.getIn().getHeader(Exchange.FILE_NAME, String.class),
                                exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getMessage()))
                        .end()
                        .to("langchain4j-ingest:photos?modality=media&documentIdHeader=CamelFileName"
                            + "&embeddingStore=#bean:imageStore&embeddingModel=#bean:imageModel");
            }
        };
    }

    @Test
    void mixedFolderIngestsIntoOneStore() throws Exception {
        NotifyBuilder ingested = new NotifyBuilder(context).fromRoute("gallery").whenCompleted(4).create();
        Path gallery = Files.createDirectories(folder.resolve("gallery"));
        Files.write(gallery.resolve("photo.png"), "png".getBytes(StandardCharsets.UTF_8));
        Files.write(gallery.resolve("song.wav"), LangChain4jIngestMediaTest.wav(50));
        Files.write(gallery.resolve("clip.mp4"), "mp4".getBytes(StandardCharsets.UTF_8));
        Files.write(gallery.resolve("manual.pdf"), "pdf".getBytes(StandardCharsets.UTF_8));

        assertThat(ingested.matches(10, TimeUnit.SECONDS)).as("all four files ingested").isTrue();

        assertThat(documentIds(allStore, allModel))
                .containsExactlyInAnyOrder("photo.png", "song.wav", "clip.mp4", "manual.pdf");
        assertThat(allModel.contentTypes())
                .containsExactlyInAnyOrder(ContentType.IMAGE, ContentType.AUDIO, ContentType.VIDEO, ContentType.PDF);
        // nothing consumed: the files are still there for the next poll, remembered by the register
        assertThat(gallery.toFile().list()).hasSize(4);
    }

    @Test
    void mediumTheModelLacksFailsPerFileWhileTheRestIngests() throws Exception {
        NotifyBuilder done = new NotifyBuilder(context).fromRoute("photos").whenCompleted(3).create();
        Path photos = Files.createDirectories(folder.resolve("photos"));
        Files.write(photos.resolve("photo.png"), "png".getBytes(StandardCharsets.UTF_8));
        Files.write(photos.resolve("song.wav"), LangChain4jIngestMediaTest.wav(50));
        Files.write(photos.resolve("manual.pdf"), "pdf".getBytes(StandardCharsets.UTF_8));

        assertThat(done.matches(10, TimeUnit.SECONDS)).as("all three files handled").isTrue();

        // the image went in; the media the model does not declare were refused one by one
        assertThat(documentIds(imageStore, imageModel)).containsExactly("photo.png");
        assertThat(refusals.keySet()).containsExactlyInAnyOrder("song.wav", "manual.pdf");
        assertThat(refusals.get("song.wav")).contains("does not support AUDIO input");
        assertThat(refusals.get("manual.pdf")).contains("does not support PDF input");
        // refused before the bytes were read: the model never saw them
        assertThat(imageModel.contentTypes()).containsExactly(ContentType.IMAGE);
    }

    /** Every document id in the store; the fake model gives a query no pull towards any document. */
    private static List<String> documentIds(
            InMemoryEmbeddingStore<TextSegment> store,
            DeterministicMediaEmbeddingModel model) {
        return store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(model.embeddingOf("any".getBytes(StandardCharsets.UTF_8)))
                .maxResults(10)
                .minScore(0.0)
                .build()).matches().stream()
                .map((EmbeddingMatch<TextSegment> match) -> match.embedded().metadata()
                        .getString(LangChain4jIngest.METADATA_DOCUMENT_ID))
                .toList();
    }
}
