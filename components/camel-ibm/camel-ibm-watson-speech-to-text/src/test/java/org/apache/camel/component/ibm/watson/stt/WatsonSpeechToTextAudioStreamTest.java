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
package org.apache.camel.component.ibm.watson.stt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.ibm.cloud.sdk.core.security.NoAuthAuthenticator;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileBinding;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recognize operation closes the audio stream it opens for a body, such as a file, and leaves an InputStream body
 * to the caller. The audio is sent with the IBM Watson SDK to a JDK HttpServer that plays Speech to Text.
 */
class WatsonSpeechToTextAudioStreamTest extends CamelTestSupport {

    // not valid UTF-8, so the test sees if the audio was read as text
    private static final byte[] AUDIO = { 'R', 'I', 'F', 'F', (byte) 0xff, (byte) 0xfe, 0, 1, (byte) 0x80 };

    private static final String RECOGNIZE_RESPONSE = """
            {"result_index":0,"results":[{"final":true,"alternatives":[{"transcript":"hello world","confidence":0.9}]}]}""";

    private static final Map<String, byte[]> REQUESTS = new ConcurrentHashMap<>();
    private static HttpServer server;

    private final List<TrackingInputStream> convertedStreams = new CopyOnWriteArrayList<>();

    @TempDir
    Path dir;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", WatsonSpeechToTextAudioStreamTest::handle);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] audio = exchange.getRequestBody().readAllBytes();
        REQUESTS.put(path, audio);

        int status = path.equals("/v1/recognize") ? 200 : 404;
        if (status == 200 && audio.length > 0 && audio[0] == 'X') {
            // audio the service cannot read
            status = 400;
        }
        byte[] response = status == 200
                ? RECOGNIZE_RESPONSE.getBytes(StandardCharsets.UTF_8)
                : "{\"code\":400,\"error\":\"unable to transcode data stream\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    @BeforeEach
    void clearRequests() {
        REQUESTS.clear();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // keep a stream body as it is sent
        context.setStreamCaching(false);
        // a body type whose stream comes from the type converter, and is remembered to check it is closed
        context.getTypeConverterRegistry().addTypeConverter(InputStream.class, AudioClip.class,
                new TypeConverterSupport() {
                    @Override
                    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
                        TrackingInputStream stream
                                = new TrackingInputStream(new ByteArrayInputStream(((AudioClip) value).data()));
                        convertedStreams.add(stream);
                        return type.cast(stream);
                    }
                });
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                WatsonSpeechToTextConfiguration configuration = new WatsonSpeechToTextConfiguration();
                configuration.setApiKey("test-key");
                configuration.setOperation(WatsonSpeechToTextOperations.recognize);
                WatsonSpeechToTextComponent component
                        = getContext().getComponent("ibm-watson-speech-to-text", WatsonSpeechToTextComponent.class);

                from("direct:recognize")
                        .to(new TestServerEndpoint("ibm-watson-speech-to-text:test", component, configuration));
            }
        };
    }

    @Test
    void theStreamConvertedFromTheBodyIsClosed() {
        Exchange result = recognize(new AudioClip(AUDIO));

        assertRecognized(result);
        assertEquals(1, convertedStreams.size());
        assertTrue(convertedStreams.get(0).closed, "the stream the producer got by conversion is closed");
    }

    @Test
    void theStreamConvertedFromTheBodyIsClosedWhenRecognitionFails() {
        Exchange result = recognize(new AudioClip(new byte[] { 'X', 1, 2 }));

        assertTrue(result.isFailed(), "the service rejected the audio");
        assertEquals(1, convertedStreams.size());
        assertTrue(convertedStreams.get(0).closed, "the stream the producer got by conversion is closed");
    }

    @Test
    void anInputStreamBodyIsLeftOpenForTheCaller() {
        TrackingInputStream body = new TrackingInputStream(new ByteArrayInputStream(AUDIO));

        Exchange result = recognize(body);

        assertRecognized(result);
        assertFalse(body.closed, "an InputStream body belongs to the caller");
    }

    @Test
    void bytesAreSentAsTheyAre() {
        assertRecognized(recognize(AUDIO));
    }

    @Test
    void noBodyFails() {
        Exchange result = recognize(null);

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertEquals("Audio input (InputStream or File) must be specified", e.getMessage());
        assertNull(REQUESTS.get("/v1/recognize"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theFileOfAFileBodyIsClosed() throws Exception {
        Path audio = Files.write(dir.resolve("audio.wav"), AUDIO);

        assertRecognized(recognize(audio.toFile()));
        assertFalse(isOpen(audio), "the file opened for the File body is closed");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theFileOfAPathBodyIsClosed() throws Exception {
        Path audio = Files.write(dir.resolve("audio.wav"), AUDIO);

        assertRecognized(recognize(audio));
        assertFalse(isOpen(audio), "the file opened for the Path body is closed");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theFileOfTheFileConsumerIsClosed() throws Exception {
        Path audio = Files.write(dir.resolve("audio.wav"), AUDIO);
        File file = audio.toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());
        genericFile.setAbsoluteFilePath(file.getAbsolutePath());
        genericFile.setFileLength(file.length());
        genericFile.setBody(file);
        genericFile.setBinding(new FileBinding());

        assertRecognized(recognize(genericFile));
        assertFalse(isOpen(audio), "the file opened for the file of the file consumer is closed");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theFileOfTheAudioFileHeaderIsClosed() throws Exception {
        Path audio = Files.write(dir.resolve("audio.wav"), AUDIO);

        Exchange result = template.send("direct:recognize", exchange -> exchange.getIn()
                .setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audio.toFile()));

        assertRecognized(result);
        assertFalse(isOpen(audio), "the file of the header is closed");
    }

    private Exchange recognize(Object body) {
        return template.send("direct:recognize", exchange -> exchange.getIn().setBody(body));
    }

    private static void assertRecognized(Exchange result) {
        if (result.getException() != null) {
            throw new AssertionError("the exchange failed: " + result.getException(), result.getException());
        }
        assertInstanceOf(SpeechRecognitionResults.class, result.getMessage().getBody());
        assertEquals("hello world", result.getMessage().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT));
        assertArrayEquals(AUDIO, REQUESTS.get("/v1/recognize"), "the audio is sent as it is");
    }

    /**
     * Whether this JVM has the file open, from the file descriptors in /proc/self/fd.
     */
    private static boolean isOpen(Path file) throws IOException {
        Path target = file.toRealPath();
        try (Stream<Path> fds = Files.list(Path.of("/proc/self/fd"))) {
            return fds.anyMatch(fd -> {
                try {
                    return Files.readSymbolicLink(fd).equals(target);
                } catch (IOException e) {
                    // the descriptor was closed while listing
                    return false;
                }
            });
        }
    }

    record AudioClip(byte[] data) {
    }

    private static final class TrackingInputStream extends FilterInputStream {

        private volatile boolean closed;

        TrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /**
     * Uses a client without IAM authentication that calls the test server.
     */
    private static final class TestServerEndpoint extends WatsonSpeechToTextEndpoint {

        private final SpeechToText client;

        TestServerEndpoint(String uri, WatsonSpeechToTextComponent component, WatsonSpeechToTextConfiguration configuration) {
            super(uri, component, configuration);
            client = new SpeechToText(new NoAuthAuthenticator());
            client.setServiceUrl("http://localhost:" + server.getAddress().getPort());
        }

        @Override
        public SpeechToText getSttClient() {
            return client;
        }
    }
}
