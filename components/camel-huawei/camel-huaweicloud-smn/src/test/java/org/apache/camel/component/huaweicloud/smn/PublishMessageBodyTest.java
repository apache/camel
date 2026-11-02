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
package org.apache.camel.component.huaweicloud.smn;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.huaweicloud.sdk.smn.v2.SmnClient;
import com.huaweicloud.sdk.smn.v2.model.PublishMessageRequest;
import com.huaweicloud.sdk.smn.v2.model.PublishMessageResponse;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.huaweicloud.smn.constants.SmnProperties;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The message the producer publishes for each kind of body, and the exchange properties it sets, with a client that
 * records the request.
 */
class PublishMessageBodyTest extends CamelTestSupport {

    private static final String MESSAGE_ID = "bf94b63a5dfb475994d3ac34664e24f2";
    private static final String REQUEST_ID = "6a63a18b8bab40ffb71ebd9cb80d0085";

    @TempDir
    Path tempDir;

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("smnClient")
    RecordingSmnClient client = new RecordingSmnClient();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String options = "&accessKey=" + testConfiguration.getProperty("accessKey")
                         + "&secretKey=" + testConfiguration.getProperty("secretKey")
                         + "&projectId=" + testConfiguration.getProperty("projectId")
                         + "&region=" + testConfiguration.getProperty("region")
                         + "&smnClient=#smnClient";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:text")
                        .setProperty(SmnProperties.NOTIFICATION_TOPIC_NAME, constant(testConfiguration.getProperty("topic")))
                        .to("hwcloud-smn:publishMessageService?operation=publishAsTextMessage" + options);

                from("direct:templated")
                        .setProperty(SmnProperties.NOTIFICATION_TOPIC_NAME, constant(testConfiguration.getProperty("topic")))
                        .setProperty(SmnProperties.TEMPLATE_NAME, constant("hello-template"))
                        .to("hwcloud-smn:publishMessageService?operation=publishAsTemplatedMessage" + options);
            }
        };
    }

    @Test
    void stringIsPublishedAsItIs() {
        template.sendBody("direct:text", "Hello €");

        assertEquals("Hello €", client.request.getBody().getMessage());
    }

    @Test
    void bytesArePublishedAsText() {
        template.sendBody("direct:text", "Hello €".getBytes(StandardCharsets.UTF_8));

        assertEquals("Hello €", client.request.getBody().getMessage());
    }

    @Test
    void streamIsPublishedAsText() {
        template.sendBody("direct:text", new ByteArrayInputStream("Hello €".getBytes(StandardCharsets.UTF_8)));

        assertEquals("Hello €", client.request.getBody().getMessage());
    }

    @Test
    void fileIsPublishedAsText() throws Exception {
        File file = Files.writeString(tempDir.resolve("message.txt"), "Hello €", StandardCharsets.UTF_8).toFile();

        template.sendBody("direct:text", file);

        assertEquals("Hello €", client.request.getBody().getMessage());
    }

    @Test
    void mapIsPublishedAsJson() {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", 1);
        order.put("status", "shipped");

        template.sendBody("direct:text", order);

        assertEquals("{\"orderId\":1,\"status\":\"shipped\"}", client.request.getBody().getMessage());
    }

    @Test
    void listIsPublishedAsJson() {
        template.sendBody("direct:text", List.of("monitors", "keyboards"));

        assertEquals("[\"monitors\",\"keyboards\"]", client.request.getBody().getMessage());
    }

    @Test
    void pojoIsPublishedAsJson() {
        template.sendBody("direct:text", new Order("monitors", 20));

        assertEquals("{\"product\":\"monitors\",\"quantity\":20}", client.request.getBody().getMessage());
    }

    @Test
    void mapIsPublishedAsJsonWithTemplate() {
        template.sendBody("direct:templated", Map.of("name", "reji"));

        assertEquals("{\"name\":\"reji\"}", client.request.getBody().getMessage());
        assertEquals("hello-template", client.request.getBody().getMessageTemplateName());
    }

    @Test
    void mapThatCannotBeJsonFails() {
        Map<String, Object> body = Map.of("key", new Object());

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:text", body));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("body cannot be published as JSON"), cause.getMessage());
        assertNull(client.request, "the message must not be published");
    }

    @Test
    void valueIsPublishedAsText() {
        UUID id = UUID.fromString("3f2b7c1e-8a4d-4e5f-9b6a-1c2d3e4f5a6b");

        template.sendBody("direct:text", id);

        assertEquals(id.toString(), client.request.getBody().getMessage());
    }

    @Test
    void noBodyWithTemplateIsNoMessage() {
        template.sendBody("direct:templated", null);

        assertNull(client.request.getBody().getMessage());
    }

    @Test
    void messageIdIsSetInCorrectlySpelledProperty() {
        Exchange exchange = template.send("direct:text", e -> e.getMessage().setBody("Hello"));

        assertEquals(MESSAGE_ID, exchange.getProperty("CamelHwCloudSmnMessageId"));
        assertEquals(REQUEST_ID, exchange.getProperty("CamelHwCloudSmnRequestId"));
    }

    @Test
    void messageIdIsStillSetInDeprecatedProperty() {
        Exchange exchange = template.send("direct:text", e -> e.getMessage().setBody("Hello"));

        assertEquals(MESSAGE_ID, exchange.getProperty("CamelHwCloudSmnMesssageId"));
    }

    /**
     * Records the request and answers with the ids of the message and of the request.
     */
    static final class RecordingSmnClient extends SmnClient {

        PublishMessageRequest request;

        RecordingSmnClient() {
            super(null);
        }

        @Override
        public PublishMessageResponse publishMessage(PublishMessageRequest request) {
            this.request = request;
            return new PublishMessageResponse().withRequestId(REQUEST_ID).withMessageId(MESSAGE_ID);
        }
    }

    record Order(String product, int quantity) {
    }
}
