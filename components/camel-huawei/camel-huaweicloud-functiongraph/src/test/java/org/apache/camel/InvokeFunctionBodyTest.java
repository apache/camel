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
package org.apache.camel;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.huaweicloud.sdk.functiongraph.v2.FunctionGraphClient;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionRequest;
import com.huaweicloud.sdk.functiongraph.v2.model.InvokeFunctionResponse;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event the producer sends to the function and the body it gives from the result, with a client that records the
 * request.
 */
class InvokeFunctionBodyTest extends CamelTestSupport {

    private static final String EVENT
            = "{\"department\":\"sales\",\"product\":\"monitors\",\"price\":20.13,\"quantity\":20,\"note\":\"5 € off\"}";
    private static final Map<String, Object> EVENT_MAP = Map.of(
            "department", "sales", "product", "monitors", "price", 20.13, "quantity", 20, "note", "5 € off");

    @TempDir
    Path tempDir;

    TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("functionGraphClient")
    RecordingClient client = new RecordingClient();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:invoke")
                        .to("hwcloud-functiongraph:invokeFunction?" +
                            "accessKey=" + testConfiguration.getProperty("accessKey") +
                            "&secretKey=" + testConfiguration.getProperty("secretKey") +
                            "&projectId=" + testConfiguration.getProperty("projectId") +
                            "&region=" + testConfiguration.getProperty("region") +
                            "&functionName=" + testConfiguration.getProperty("functionName") +
                            "&functionGraphClient=#functionGraphClient");
            }
        };
    }

    @Test
    void stringEventIsSentAsJsonObject() {
        template.requestBody("direct:invoke", EVENT);

        assertEquals(EVENT_MAP, client.request.getBody());
    }

    @Test
    void bytesEventIsSentAsJsonObject() {
        template.requestBody("direct:invoke", EVENT.getBytes(StandardCharsets.UTF_8));

        assertEquals(EVENT_MAP, client.request.getBody());
    }

    @Test
    void streamEventIsSentAsJsonObject() {
        template.requestBody("direct:invoke", new ByteArrayInputStream(EVENT.getBytes(StandardCharsets.UTF_8)));

        assertEquals(EVENT_MAP, client.request.getBody());
    }

    @Test
    void fileEventIsSentAsJsonObject() throws Exception {
        File file = Files.writeString(tempDir.resolve("event.json"), EVENT, StandardCharsets.UTF_8).toFile();

        template.requestBody("direct:invoke", file);

        assertEquals(EVENT_MAP, client.request.getBody());
    }

    @Test
    void genericFileEventIsSentAsJsonObject() throws Exception {
        File file = Files.writeString(tempDir.resolve("event.json"), EVENT, StandardCharsets.UTF_8).toFile();

        template.requestBody("direct:invoke", FileConsumer.asGenericFile(tempDir.toString(), file, "UTF-8", false));

        assertEquals(EVENT_MAP, client.request.getBody());
    }

    @Test
    void mapEventIsSentAsItIs() {
        Map<String, Object> event = new HashMap<>(EVENT_MAP);

        template.requestBody("direct:invoke", event);

        assertSame(event, client.request.getBody());
    }

    @Test
    void jsonArrayEventFails() {
        IllegalArgumentException e = assertInvokeFails("[{\"id\":1},{\"id\":2}]");

        assertTrue(e.getMessage().contains("the body is a JSON array: wrap it in a JSON object"), e.getMessage());
    }

    @Test
    void jsonNumberEventAsBytesFails() {
        IllegalArgumentException e = assertInvokeFails("42".getBytes(StandardCharsets.UTF_8));

        assertTrue(e.getMessage().contains("the body is a JSON number: wrap it in a JSON object"), e.getMessage());
    }

    @Test
    void invalidJsonEventFails() {
        IllegalArgumentException e = assertInvokeFails("not json".getBytes(StandardCharsets.UTF_8));

        assertTrue(e.getMessage().contains("the body ([B) is not valid JSON"), e.getMessage());
    }

    @Test
    void pojoEventFails() {
        IllegalArgumentException e = assertInvokeFails(new Order("monitors", 20));

        assertTrue(e.getMessage().contains("the body (" + Order.class.getName() + ") is not valid JSON"), e.getMessage());
    }

    @Test
    void noEventFails() {
        IllegalArgumentException e = assertInvokeFails(null);

        assertTrue(e.getMessage().contains("Exchange body is mandatory"), e.getMessage());
    }

    @Test
    void resultWithoutBodyFieldIsTheBody() {
        client.result = "{\"orderId\":1621950031517,\"status\":\"order submitted successfully\"}";

        assertEquals(client.result, template.requestBody("direct:invoke", EVENT, String.class));
    }

    @Test
    void jsonArrayResultIsTheBody() {
        client.result = "[{\"id\":1},{\"id\":2}]";

        assertEquals(client.result, template.requestBody("direct:invoke", EVENT, String.class));
    }

    @Test
    void plainTextResultIsTheBody() {
        client.result = "Hello, World!";

        assertEquals(client.result, template.requestBody("direct:invoke", EVENT, String.class));
    }

    @Test
    void bodyFieldOfResultIsTheBody() {
        client.result = "{\"statusCode\":200,\"isBase64Encoded\":false,\"body\":\"{\\\"key\\\": \\\"value\\\"}\","
                        + "\"headers\":{\"Content-Type\":\"application/json\"}}";

        assertEquals("{\"key\": \"value\"}", template.requestBody("direct:invoke", EVENT, String.class));
    }

    @Test
    void nullBodyFieldOfResultIsNoBody() {
        client.result = "{\"statusCode\":204,\"body\":null}";

        assertNull(template.requestBody("direct:invoke", EVENT));
    }

    @Test
    void noResultIsNoBody() {
        client.result = null;

        assertNull(template.requestBody("direct:invoke", EVENT));
    }

    private IllegalArgumentException assertInvokeFails(Object body) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:invoke", body));
        assertNull(client.request, "the function must not be invoked");
        return assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    /**
     * Records the request and gives the result that the test sets.
     */
    static final class RecordingClient extends FunctionGraphClient {

        InvokeFunctionRequest request;
        String result = "{\"statusCode\":200,\"body\":\"done\"}";

        RecordingClient() {
            super(null);
        }

        @Override
        public InvokeFunctionResponse invokeFunction(InvokeFunctionRequest request) {
            this.request = request;
            return new InvokeFunctionResponse().withRequestId("1939bbbb-4009-4685-bcc0-2ff0381fa911").withStatus(200)
                    .withResult(result);
        }
    }

    record Order(String product, int quantity) {
    }
}
