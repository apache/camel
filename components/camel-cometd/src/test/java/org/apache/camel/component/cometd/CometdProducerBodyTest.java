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
package org.apache.camel.component.cometd;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.cometd.bayeux.Message;
import org.cometd.client.BayeuxClient;
import org.cometd.client.http.jetty.JettyHttpClientTransport;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body a cometd producer publishes, and the body a cometd consumer replies with, as a BayeuxClient receives them.
 */
public class CometdProducerBodyTest extends CamelTestSupport {

    private static final String CHANNEL = "/test/body";
    private static final String SERVICE_CHANNEL = "/service/reply";

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @TempDir
    Path tempDir;

    private final BlockingQueue<Message> received = new LinkedBlockingQueue<>();
    private HttpClient httpClient;
    private BayeuxClient client;

    @BeforeEach
    void connectClient() throws Exception {
        httpClient = new HttpClient();
        httpClient.start();
        client = new BayeuxClient(
                "http://127.0.0.1:" + port.getPort() + "/cometd", new JettyHttpClientTransport(null, httpClient));
        client.handshake();
        assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));

        CountDownLatch subscribed = new CountDownLatch(2);
        client.getChannel(CHANNEL).subscribe((channel, message) -> received.add(message),
                message -> subscribed.countDown());
        client.getChannel(SERVICE_CHANNEL).subscribe((channel, message) -> received.add(message),
                message -> subscribed.countDown());
        assertTrue(subscribed.await(5, TimeUnit.SECONDS), "Should subscribe within timeout");
    }

    @AfterEach
    void disconnectClient() throws Exception {
        client.disconnect();
        client.waitFor(5000, BayeuxClient.State.DISCONNECTED);
        httpClient.stop();
    }

    @Test
    void testTextBody() throws Exception {
        template.sendBody("direct:input", "Hello World");

        assertEquals("Hello World", receive().getData());
    }

    @Test
    void testInputStreamBody() throws Exception {
        template.sendBody("direct:input", new ByteArrayInputStream("Hello Stream".getBytes(StandardCharsets.UTF_8)));

        assertEquals("Hello Stream", receive().getData());
    }

    @Test
    void testStreamCacheBody() throws Exception {
        template.sendBody("direct:input", new ByteArrayInputStreamCache(
                new ByteArrayInputStream("Hello Stream Cache".getBytes(StandardCharsets.UTF_8))));

        assertEquals("Hello Stream Cache", receive().getData());
    }

    @Test
    void testReaderBody() throws Exception {
        template.sendBody("direct:input", new StringReader("Hello Reader"));

        assertEquals("Hello Reader", receive().getData());
    }

    @Test
    void testFileBody() throws Exception {
        Path file = Files.writeString(tempDir.resolve("hello.txt"), "Hello File");

        template.sendBody("direct:input", file.toFile());
        assertEquals("Hello File", receive().getData());

        template.sendBody("direct:input", file);
        assertEquals("Hello File", receive().getData());
    }

    @Test
    void testFileConsumerBody() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "Hello File Consumer");

        context.getRouteController().startRoute("file");

        assertEquals("Hello File Consumer", receive().getData());
    }

    @Test
    void testNullBody() throws Exception {
        template.sendBody("direct:input", null);

        Message message = receive();
        assertEquals(CHANNEL, message.getChannel());
        assertTrue(message.containsKey(Message.DATA_FIELD));
        assertNull(message.getData());
    }

    @Test
    void testNullReply() throws Exception {
        client.getChannel(SERVICE_CHANNEL).publish("Hello");

        Message message = receive();
        assertEquals(SERVICE_CHANNEL, message.getChannel());
        assertTrue(message.containsKey(Message.DATA_FIELD));
        assertNull(message.getData());
    }

    @Test
    void testStreamReply() throws Exception {
        client.getChannel(SERVICE_CHANNEL).publish("stream");

        assertEquals("Reply stream", receive().getData());
    }

    private Message receive() throws InterruptedException {
        Message message = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "Should receive a message within timeout");
        return message;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String options = "?baseResource=file:./target/test-classes/webapp&timeout=240000&interval=0&maxInterval=30000"
                         + "&multiFrameInterval=1500&jsonCommented=true&logLevel=2";
        String producer = "cometd://127.0.0.1:" + port.getPort() + CHANNEL + options;
        String consumer = "cometd://127.0.0.1:" + port.getPort() + SERVICE_CHANNEL + options;

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:input").to(producer);

                File dir = tempDir.toFile();
                fromF("file:%s?noop=true&initialDelay=0&delay=10", dir.getAbsolutePath()).routeId("file").autoStartup(false)
                        .to(producer);

                from(consumer)
                        .setExchangePattern(ExchangePattern.InOut)
                        .choice()
                        .when(body().isEqualTo("stream"))
                        .process(exchange -> exchange.getMessage().setBody(
                                new ByteArrayInputStream("Reply stream".getBytes(StandardCharsets.UTF_8))))
                        .otherwise()
                        .process(exchange -> exchange.getMessage().setBody(null));
            }
        };
    }
}
