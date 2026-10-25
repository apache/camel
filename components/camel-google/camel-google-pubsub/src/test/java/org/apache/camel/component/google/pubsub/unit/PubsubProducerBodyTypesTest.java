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
package org.apache.camel.component.google.pubsub.unit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.file.FileBinding;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubProducer;
import org.apache.camel.component.google.pubsub.serializer.DefaultGooglePubsubSerializer;
import org.apache.camel.converter.stream.ByteArrayInputStreamCache;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the bytes the producer publishes for each kind of body.
 */
class PubsubProducerBodyTypesTest {

    private static final String CONTENT = "Hello Pub/Sub";

    private final GooglePubsubEndpoint endpoint = mock();
    private final GooglePubsubComponent component = mock();
    private final Publisher publisher = mock();
    private final DefaultCamelContext context = new DefaultCamelContext();

    @TempDir
    Path dir;

    @BeforeEach
    void setUp() throws Exception {
        context.start();
        when(endpoint.getProjectId()).thenReturn("project");
        when(endpoint.getDestinationName()).thenReturn("topic");
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getSerializer()).thenReturn(new DefaultGooglePubsubSerializer());
        when(component.getPublisher(anyString(), any())).thenReturn(publisher);
        when(publisher.publish(any())).thenReturn(ApiFutures.immediateFuture("id"));
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void aStringIsSentAsUtf8() throws Exception {
        assertThat(publish("Grüße")).isEqualTo("Grüße".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bytesAreSentAsTheyAre() throws Exception {
        byte[] bytes = { 1, 2, 3 };
        assertThat(publish(bytes)).isEqualTo(bytes);
    }

    @Test
    void aStreamIsSentAsItsContent() throws Exception {
        assertThat(publish(new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8))))
                .isEqualTo(CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aStreamCacheIsSentAsItsContent() throws Exception {
        assertThat(publish(new ByteArrayInputStreamCache(new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)))))
                .isEqualTo(CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aFileIsSentAsItsContentNotAsASerializedFile() throws Exception {
        File file = Files.writeString(dir.resolve("hello.txt"), CONTENT).toFile();
        assertThat(publish(file)).isEqualTo(CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aPathIsSentAsTheContentOfTheFile() throws Exception {
        Path path = Files.writeString(dir.resolve("hello.txt"), CONTENT);
        assertThat(publish(path)).isEqualTo(CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theFileOfTheFileConsumerIsSentAsItsContent() throws Exception {
        File file = Files.writeString(dir.resolve("hello.txt"), CONTENT).toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());
        genericFile.setAbsoluteFilePath(file.getAbsolutePath());
        genericFile.setBody(file);
        genericFile.setBinding(new FileBinding());

        assertThat(publish(genericFile)).isEqualTo(CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aMapStillGoesThroughTheSerializer() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("KEY", "VALUE");

        byte[] data = publish(map);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            assertThat(in.readObject()).isEqualTo(map);
        }
    }

    @Test
    void noBodyIsRejected() {
        GooglePubsubProducer producer = new GooglePubsubProducer(endpoint);
        Exchange exchange = new DefaultExchange(context);

        // publishing a serialized null (AC ED 00 05 70) is not a message anybody can use
        assertThatThrownBy(() -> producer.process(exchange))
                .isInstanceOf(InvalidPayloadException.class);
        verify(publisher, never()).publish(any());
    }

    private byte[] publish(Object body) throws Exception {
        GooglePubsubProducer producer = new GooglePubsubProducer(endpoint);
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);

        producer.process(exchange);

        ArgumentCaptor<PubsubMessage> message = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(publisher).publish(message.capture());
        return message.getValue().getData().toByteArray();
    }
}
