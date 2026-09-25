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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class LangChain4jIngestMediaTest extends CamelTestSupport {

    @BindToRegistry("store")
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

    @BindToRegistry("model")
    private final DeterministicMediaEmbeddingModel model = new DeterministicMediaEmbeddingModel(16);

    /** Claims taken on the register, so a test can assert a rejected delivery never took one. */
    private final AtomicInteger claims = new AtomicInteger();

    @BindToRegistry("register")
    private final IdempotentRepository register = new KeyValueIdempotentRepository() {
        @Override
        public boolean add(String key) {
            claims.incrementAndGet();
            return super.add(key);
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:media")
                        .to("langchain4j-ingest:library?modality=media&documentIdHeader=CamelFileName");
                // case and parameters are normalised before the type reaches the model
                from("direct:typed")
                        .to("langchain4j-ingest:typed?modality=media&documentIdHeader=CamelFileName"
                            + "&contentType=Image/PNG;charset=binary");
                from("direct:capped")
                        .to("langchain4j-ingest:capped?modality=media&documentIdHeader=CamelFileName"
                            + "&maxDocumentSize=100");
                from("direct:min")
                        .to("langchain4j-ingest:min?modality=media&documentIdHeader=CamelFileName"
                            + "&minDocumentSize=1000000");
                from("direct:dedup")
                        .to("langchain4j-ingest:dedup?modality=media&documentIdHeader=CamelFileName"
                            + "&idempotentRepository=#bean:register");
                // the batch size does not apply to media, so even an invalid value must not fail the start
                from("direct:batch0")
                        .to("langchain4j-ingest:batch0?modality=media&documentIdHeader=CamelFileName"
                            + "&embeddingBatchSize=0");
            }
        };
    }

    @Test
    void ingestsOneVectorPerDocumentWithIdentityMetadata() {
        byte[] wav = wav(200);

        IngestResult result = template.requestBodyAndHeader("direct:media", wav,
                Exchange.FILE_NAME, "song-1.wav", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(result.pipeline()).isEqualTo("library");
        assertThat(result.documentId()).isEqualTo("song-1.wav");
        assertThat(result.segmentsWritten()).isEqualTo(1);
        assertThat(model.mimeTypes()).containsExactly("audio/wav");
        assertThat(model.contentTypes()).containsExactly(ContentType.AUDIO);

        List<EmbeddingMatch<TextSegment>> matches = search(wav);
        assertThat(matches).hasSize(1);
        EmbeddingMatch<TextSegment> match = matches.get(0);
        // the same file embeds to the same vector: a query by it is an exact hit
        assertThat(match.score()).isGreaterThan(0.99);
        // the placeholder segment carries the id as text and the same identity stamps as text segments
        assertThat(match.embedded().text()).isEqualTo("song-1.wav");
        assertThat(match.embedded().metadata().getString(LangChain4jIngest.METADATA_PIPELINE)).isEqualTo("library");
        assertThat(match.embedded().metadata().getString(LangChain4jIngest.METADATA_DOCUMENT_ID))
                .isEqualTo("song-1.wav");
    }

    @Test
    void everyMediumIsTypedByItsExtension() {
        // the bodies are fake: the bytes of the file name, not a real image or video. Nothing in
        // the pipeline inspects the content - the medium comes from the extension and the fake
        // model hashes whatever bytes it gets - so real fixtures would prove nothing more
        for (String name : List.of("photo.png", "clip.mp4", "manual.pdf", "voice.opus", "recording.webm")) {
            IngestResult result = template.requestBodyAndHeader("direct:media",
                    name.getBytes(StandardCharsets.UTF_8), Exchange.FILE_NAME, name, IngestResult.class);
            assertThat(result.outcome()).as(name).isEqualTo(IngestResult.Outcome.INGESTED);
            assertThat(result.segmentsWritten()).as(name).isEqualTo(1);
        }

        // Camel's MIME table decides: an .opus file is Ogg-encapsulated, a .webm recording is video
        assertThat(model.contentTypes()).containsExactly(
                ContentType.IMAGE, ContentType.VIDEO, ContentType.PDF, ContentType.AUDIO, ContentType.VIDEO);
        assertThat(model.mimeTypes())
                .containsExactly("image/png", "video/mp4", "application/pdf", "audio/ogg", "video/webm");
    }

    @Test
    void emptyBodyAnswersEmpty() {
        IngestResult result = template.requestBodyAndHeader("direct:media", new byte[0],
                Exchange.FILE_NAME, "silence.wav", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.EMPTY);
        assertThat(result.segmentsWritten()).isZero();
    }

    @Test
    void nullBodyAnswersEmptyButAnUnconvertibleOneFails() {
        IngestResult result = template.requestBodyAndHeader("direct:media", null,
                Exchange.FILE_NAME, "missing.wav", IngestResult.class);
        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.EMPTY);

        // a POJO has no converter to bytes: a silent null conversion would answer EMPTY and
        // release the claim, hiding a wiring mistake - it must fail instead
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:media", new Object(),
                Exchange.FILE_NAME, "pojo.wav"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("InvalidPayloadException");
    }

    @Test
    void tooSmallMediaIsFiltered() {
        IngestResult result = template.requestBodyAndHeader("direct:min", wav(50),
                Exchange.FILE_NAME, "tiny.wav", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.FILTERED);
    }

    @Test
    void oversizedMediaFailsAndTheCapCountsBytes() {
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:capped", wav(200),
                Exchange.FILE_NAME, "big.wav"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("exceeds maxDocumentSize")
                .hasStackTraceContaining("bytes");
    }

    @Test
    void mimeTypeComesFromTheExtensionOrTheNormalisedOption() {
        // the same WAV bytes travel under every name below: only the name, or the option, decides
        // the type, never the content
        byte[] clip = wav(50);

        template.requestBodyAndHeader("direct:media", clip, Exchange.FILE_NAME, "clip.mp3", IngestResult.class);
        template.requestBodyAndHeader("direct:typed", clip, Exchange.FILE_NAME, "clip.wav", IngestResult.class);

        // the option wins over the extension, lower-cased and without its parameter
        assertThat(model.mimeTypes()).containsExactly("audio/mpeg", "image/png");
        assertThat(model.contentTypes()).containsExactly(ContentType.AUDIO, ContentType.IMAGE);

        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:media", clip,
                Exchange.FILE_NAME, "clip.zzz"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("cannot tell the media type")
                .hasStackTraceContaining("contentType");
        // a known type that is no medium: Camel's table knows .txt, the pipeline cannot embed it
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:media", clip,
                Exchange.FILE_NAME, "notes.txt"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("is not a media type");
        // a bare id without extension is not typed by its name, even one that reads like an extension
        assertThatThrownBy(() -> template.requestBodyAndHeader("direct:media", clip,
                Exchange.FILE_NAME, "mp3"))
                .isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("cannot tell the media type");
    }

    @Test
    void duplicateIdIsSkippedWithARepository() {
        byte[] wav = wav(50);

        IngestResult first = template.requestBodyAndHeader("direct:dedup", wav,
                Exchange.FILE_NAME, "song-2.wav", IngestResult.class);
        IngestResult second = template.requestBodyAndHeader("direct:dedup", wav,
                Exchange.FILE_NAME, "song-2.wav", IngestResult.class);

        assertThat(first.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
        assertThat(second.outcome()).isEqualTo(IngestResult.Outcome.SKIPPED);
        assertThat(search(wav)).hasSize(1);
    }

    @Test
    void unknownTypeFailsBeforeTheClaimAndTheBodyRead() {
        int claimsBefore = claims.get();

        Throwable thrown = catchThrowable(() -> template.requestBodyAndHeader("direct:dedup", unreadableBody(),
                Exchange.FILE_NAME, "clip.zzz"));

        assertThat(thrown).isInstanceOf(CamelExecutionException.class)
                .hasStackTraceContaining("cannot tell the media type");
        assertThat(stackTraceOf(thrown)).as("the body must not have been read").doesNotContain("body must not be read");
        assertThat(claims.get()).as("no claim for a document whose type cannot be told").isEqualTo(claimsBefore);
    }

    @Test
    void mediumTheModelLacksFailsBeforeTheBodyRead() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store", new InMemoryEmbeddingStore<TextSegment>());
            context.getRegistry().bind("model", new DeterministicMediaEmbeddingModel(16, Set.of(ContentType.AUDIO)));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").to("langchain4j-ingest:pipe?modality=media&documentIdHeader=CamelFileName");
                }
            });
            context.start();
            ProducerTemplate producer = context.createProducerTemplate();

            Throwable thrown = catchThrowable(() -> producer.requestBodyAndHeader("direct:in", unreadableBody(),
                    Exchange.FILE_NAME, "photo.png"));

            assertThat(thrown).isInstanceOf(CamelExecutionException.class)
                    .hasStackTraceContaining("does not support IMAGE input")
                    .hasStackTraceContaining("photo.png");
            assertThat(stackTraceOf(thrown)).as("the body must not have been read")
                    .doesNotContain("body must not be read");
        }
    }

    @Test
    void batchSizeIsNotValidatedInMediaMode() {
        IngestResult result = template.requestBodyAndHeader("direct:batch0", wav(50),
                Exchange.FILE_NAME, "song-3.wav", IngestResult.class);

        assertThat(result.outcome()).isEqualTo(IngestResult.Outcome.INGESTED);
    }

    @Test
    void textOnlyModelFailsTheStart() throws Exception {
        assertStartFails(new DeterministicEmbeddingModel(16), "langchain4j-ingest:pipe?modality=media",
                "does not support any media input", "supportedContentTypes");
    }

    @Test
    void modelWithoutContentTypesFailsTheStart() throws Exception {
        assertStartFails(new DeterministicEmbeddingModel(16) {
            @Override
            public Set<ContentType> supportedContentTypes() {
                return null;
            }
        }, "langchain4j-ingest:pipe?modality=media", "does not support any media input", "are null");
    }

    @Test
    void contentTypeTheModelLacksFailsTheStart() throws Exception {
        assertStartFails(new DeterministicMediaEmbeddingModel(16, Set.of(ContentType.AUDIO)),
                "langchain4j-ingest:pipe?modality=media&contentType=image/png",
                "does not support IMAGE input", "contentType option");
    }

    @Test
    void listenerWrappedModelFailsTheStart() throws Exception {
        // LangChain4j wraps the model in a ListeningEmbeddingModel, which builds the listener
        // context from the input text and would fail every media request with an opaque error
        assertStartFails(new DeterministicMediaEmbeddingModel(16).addListener(new EmbeddingModelListener() {
        }), "langchain4j-ingest:pipe?modality=media", "EmbeddingModelListeners attached");
    }

    private static void assertStartFails(Object model, String uri, String... messageFragments) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getRegistry().bind("store", new InMemoryEmbeddingStore<TextSegment>());
            context.getRegistry().bind("model", model);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:in").to(uri);
                }
            });

            var assertion = assertThatThrownBy(context::start);
            for (String fragment : messageFragments) {
                assertion.hasStackTraceContaining(fragment);
            }
        }
    }

    private List<EmbeddingMatch<TextSegment>> search(byte[] bytes) {
        return store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(model.embeddingOf(bytes))
                .maxResults(10)
                .build()).matches();
    }

    /** A body whose read fails loudly, so a test can prove the type was rejected before any read. */
    private static InputStream unreadableBody() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("body must not be read");
            }
        };
    }

    private static String stackTraceOf(Throwable thrown) {
        StringWriter trace = new StringWriter();
        thrown.printStackTrace(new PrintWriter(trace));
        return trace.toString();
    }

    /**
     * A mono 16 kHz 16-bit PCM WAV of the given length holding a 440 Hz tone, its RIFF header written by hand: a real
     * file with no binary fixture, and no java.desktop module, which a headless JDK may lack.
     */
    static byte[] wav(int millis) {
        int sampleRate = 16_000;
        int frames = sampleRate * millis / 1000;
        int dataSize = frames * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataSize)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1) // PCM
                .putShort((short) 1) // mono
                .putInt(sampleRate)
                .putInt(sampleRate * 2) // byte rate
                .putShort((short) 2) // block align
                .putShort((short) 16) // bits per sample
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataSize);
        for (int i = 0; i < frames; i++) {
            buffer.putShort((short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * Short.MAX_VALUE / 4));
        }
        return buffer.array();
    }
}
