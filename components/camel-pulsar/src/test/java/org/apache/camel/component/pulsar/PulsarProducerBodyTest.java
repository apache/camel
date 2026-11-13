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
package org.apache.camel.component.pulsar;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The producer sends the body as a byte array. A body that does not convert to one, and no body, fail unless the
 * deprecated allowJavaSerializedObject option turns the Java serialization of the body back on.
 */
public class PulsarProducerBodyTest extends CamelTestSupport {

    private final List<byte[]> sent = new ArrayList<>();

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearSent() {
        sent.clear();
    }

    @Test
    public void testTextIsSentAsItsBytes() {
        Exchange out = template.send("direct:start", e -> e.getIn().setBody("Hello World"));

        assertNull(out.getException());
        assertArrayEquals("Hello World".getBytes(StandardCharsets.UTF_8), sent.get(0));
    }

    @Test
    public void testBytesAreSentAsTheyAre() {
        byte[] bytes = { 1, 2, 3 };
        Exchange out = template.send("direct:start", e -> e.getIn().setBody(bytes));

        assertNull(out.getException());
        assertArrayEquals(bytes, sent.get(0));
    }

    @Test
    public void testStreamIsSentAsItsContent() {
        Exchange out = template.send("direct:start",
                e -> e.getIn().setBody(new ByteArrayInputStream("from a stream".getBytes(StandardCharsets.UTF_8))));

        assertNull(out.getException());
        assertEquals("from a stream", new String(sent.get(0), StandardCharsets.UTF_8));
    }

    @Test
    public void testFileIsSentAsItsContent() throws Exception {
        File file = Files.writeString(tempDir.resolve("order.json"), "{\"id\":1}").toFile();
        Exchange out = template.send("direct:start", e -> e.getIn().setBody(file));

        assertNull(out.getException());
        assertEquals("{\"id\":1}", new String(sent.get(0), StandardCharsets.UTF_8));
    }

    @Test
    public void testMapFails() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", 1);
        Exchange out = template.send("direct:start", e -> e.getIn().setBody(map));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, out.getException());
        assertTrue(cause.getMessage().contains("marshal it first"), cause.getMessage());
        assertTrue(sent.isEmpty(), "nothing should be sent");
    }

    @Test
    public void testSerializablePojoFails() {
        Exchange out = template.send("direct:start", e -> e.getIn().setBody(new Order(42)));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, out.getException());
        assertTrue(cause.getMessage().contains(Order.class.getCanonicalName()), cause.getMessage());
        assertTrue(sent.isEmpty(), "nothing should be sent");
    }

    @Test
    public void testNoBodyFails() {
        Exchange out = template.send("direct:start", e -> e.getIn().setBody(null));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, out.getException());
        assertTrue(cause.getMessage().contains("without a body"), cause.getMessage());
        assertTrue(sent.isEmpty(), "nothing should be sent");
    }

    @Test
    public void testAllowJavaSerializedObjectSerializesAMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", 1);
        Exchange out = template.send("direct:legacy", e -> e.getIn().setBody(map));

        assertNull(out.getException());
        byte[] payload = sent.get(0);
        // the Java serialization stream magic
        assertEquals((byte) 0xAC, payload[0]);
        assertEquals((byte) 0xED, payload[1]);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(payload))) {
            assertEquals(map, in.readObject());
        }
    }

    @Test
    public void testAllowJavaSerializedObjectStillSendsTextAsItsBytes() {
        Exchange out = template.send("direct:legacy", e -> e.getIn().setBody("Hello World"));

        assertNull(out.getException());
        assertArrayEquals("Hello World".getBytes(StandardCharsets.UTF_8), sent.get(0));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        final TypedMessageBuilder<byte[]> messageBuilder = mock(TypedMessageBuilder.class);
        when(messageBuilder.value(any())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return messageBuilder;
        });
        when(messageBuilder.sendAsync()).thenReturn(CompletableFuture.completedFuture(mock(MessageId.class)));

        final Producer<byte[]> pulsarProducer = mock(Producer.class);
        when(pulsarProducer.newMessage()).thenReturn(messageBuilder);

        final ProducerBuilder<byte[]> producerBuilder = mock(ProducerBuilder.class, RETURNS_SELF);
        when(producerBuilder.create()).thenReturn(pulsarProducer);

        final PulsarClient pulsarClient = mock(PulsarClient.class);
        when(pulsarClient.newProducer()).thenReturn(producerBuilder);

        final PulsarComponent component = new PulsarComponent(context);
        component.setPulsarClient(pulsarClient);
        context.addComponent("pulsar", component);

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("pulsar:persistent://public/default/camel-body-test");
                from("direct:legacy")
                        .to("pulsar:persistent://public/default/camel-body-legacy?allowJavaSerializedObject=true");
            }
        };
    }

    public record Order(int id) implements Serializable {
    }
}
