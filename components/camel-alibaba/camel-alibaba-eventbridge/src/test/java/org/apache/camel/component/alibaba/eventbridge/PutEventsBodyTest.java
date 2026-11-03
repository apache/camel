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
package org.apache.camel.component.alibaba.eventbridge;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.aliyun.eventbridge.EventBridgeClient;
import com.aliyun.eventbridge.models.CloudEvent;
import com.aliyun.eventbridge.models.PutEventsResponse;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The JSON text of the event data can come as a String, or as the bytes, the stream or the file of a consumer.
 */
class PutEventsBodyTest extends CamelTestSupport {

    private static final String JSON = "{\"orderId\":\"123\",\"city\":\"Zürich\"}";

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("eventBridgeClient")
    EventBridgeClient eventBridgeClient = mock(EventBridgeClient.class);

    @TempDir
    Path dir;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:put")
                        .to("alibaba-eventbridge:putEvents"
                            + "?eventBusName=" + testConfiguration.getProperty("eventBusName")
                            + "&eventSource=" + testConfiguration.getProperty("eventSource")
                            + "&eventType=" + testConfiguration.getProperty("eventType")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&eventBridgeClient=#eventBridgeClient");
            }
        };
    }

    @Test
    void aStringIsTheJsonOfTheEvent() {
        assertTheEventOf(JSON);
    }

    @Test
    void bytesAreReadAsJsonText() {
        assertTheEventOf(JSON.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aStreamIsReadAsJsonText() {
        assertTheEventOf(new ByteArrayInputStream(JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aFileIsReadAsJsonText() throws Exception {
        File file = Files.writeString(dir.resolve("event.json"), JSON).toFile();

        assertTheEventOf(file);
    }

    @Test
    void theFileOfTheFileConsumerIsReadAsJsonText() throws Exception {
        File file = Files.writeString(dir.resolve("event.json"), JSON).toFile();
        GenericFile<File> genericFile = FileConsumer.asGenericFile(dir.toString(), file, null, false);

        assertTheEventOf(genericFile);
    }

    @Test
    void bytesAreReadInTheCharsetOfTheExchange() {
        stubPutEvents();

        template.sendBodyAndHeader("direct:put", JSON.getBytes(StandardCharsets.ISO_8859_1),
                Exchange.CHARSET_NAME, "ISO-8859-1");

        List<CloudEvent> events = publishedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getData()).isEqualTo(JSON.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aListOfBytesIsABatchOfEvents() {
        stubPutEvents();
        String other = "{\"orderId\":\"456\"}";

        template.sendBody("direct:put",
                List.of(JSON.getBytes(StandardCharsets.UTF_8), other.getBytes(StandardCharsets.UTF_8)));

        List<CloudEvent> events = publishedEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getData()).isEqualTo(JSON.getBytes(StandardCharsets.UTF_8));
        assertThat(events.get(1).getData()).isEqualTo(other.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void noBodyFails() {
        stubPutEvents();

        assertThatThrownBy(() -> template.sendBody("direct:put", null))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause().hasMessageStartingWith("Exchange body is null");

        verify(eventBridgeClient, never()).putEvents(anyList());
    }

    @Test
    void aPojoFailsInsteadOfBeingSentAsItsToString() {
        stubPutEvents();

        assertThatThrownBy(() -> template.sendBody("direct:put", new Order("123")))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause().hasMessageContaining(Order.class.getName()).hasMessageContaining("is not supported");

        verify(eventBridgeClient, never()).putEvents(anyList());
    }

    @Test
    void anEmptySetFailsInsteadOfBeingSentAsAnEmptyEvent() {
        stubPutEvents();

        assertThatThrownBy(() -> template.sendBody("direct:put", Set.of()))
                .isInstanceOf(CamelExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .cause().hasMessageContaining("is not supported");

        verify(eventBridgeClient, never()).putEvents(anyList());
    }

    private void assertTheEventOf(Object body) {
        stubPutEvents();

        template.sendBody("direct:put", body);

        List<CloudEvent> events = publishedEvents();
        assertThat(events).hasSize(1);
        CloudEvent event = events.get(0);
        assertThat(event.getData()).isEqualTo(JSON.getBytes(StandardCharsets.UTF_8));
        assertThat(event.getSource()).isEqualTo(testConfiguration.getProperty("eventSource"));
        assertThat(event.getType()).isEqualTo(testConfiguration.getProperty("eventType"));
        assertThat(event.getExtensions().get("aliyuneventbusname"))
                .isEqualTo(testConfiguration.getProperty("eventBusName"));
    }

    private void stubPutEvents() {
        PutEventsResponse response = new PutEventsResponse();
        response.setRequestId("req-eb-body");
        response.setFailedEntryCount(0);
        when(eventBridgeClient.putEvents(anyList())).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private List<CloudEvent> publishedEvents() {
        ArgumentCaptor<List<CloudEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(eventBridgeClient).putEvents(events.capture());
        return events.getValue();
    }

    private record Order(String id) {
    }
}
