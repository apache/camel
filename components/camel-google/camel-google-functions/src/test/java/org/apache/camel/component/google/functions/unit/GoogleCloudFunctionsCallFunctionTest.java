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
package org.apache.camel.component.google.functions.unit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.cloud.functions.v1.CallFunctionRequest;
import com.google.cloud.functions.v1.CallFunctionResponse;
import com.google.protobuf.AbstractMessage;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileBinding;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the data callFunction sends for each kind of body, and that a call whose response has an error fails the
 * exchange.
 */
class GoogleCloudFunctionsCallFunctionTest extends GoogleCloudFunctionsBaseTest {

    private static final String FUNCTION = "projects/project123/locations/location123/functions/myCamelFunction";

    @TempDir
    Path dir;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:callFunction")
                        .to("google-functions://myCamelFunction?project=project123&location=location123"
                            + "&operation=callFunction");
                from("direct:callFunctionPojo")
                        .to("google-functions://myCamelFunction?project=project123&location=location123"
                            + "&operation=callFunction&pojoRequest=true");
            }
        };
    }

    @Test
    void aFailedCallFailsTheExchange() {
        CallFunctionResponse response = CallFunctionResponse.newBuilder().setExecutionId("exec-1")
                .setError("Error: function crashed").build();
        mockCloudFunctionsService.addResponse(response);

        Exchange exchange = template.request("direct:callFunction", e -> e.getIn().setBody("data"));

        CamelExchangeException cause = assertInstanceOf(CamelExchangeException.class, exchange.getException());
        assertTrue(cause.getMessage()
                .startsWith("The function " + FUNCTION + " failed in execution exec-1: Error: function crashed"),
                cause.getMessage());
        assertEquals(response, exchange.getMessage().getHeader(GoogleCloudFunctionsConstants.RESPONSE_OBJECT));
        assertEquals("data", exchange.getMessage().getBody());
    }

    @Test
    void aFailedCallFailsTheExchangeWithPojoRequest() {
        CallFunctionResponse response = CallFunctionResponse.newBuilder().setError("Error: function crashed").build();
        mockCloudFunctionsService.addResponse(response);
        CallFunctionRequest request = CallFunctionRequest.newBuilder().setName(FUNCTION).setData("data").build();

        Exchange exchange = template.request("direct:callFunctionPojo", e -> e.getIn().setBody(request));

        CamelExchangeException cause = assertInstanceOf(CamelExchangeException.class, exchange.getException());
        assertTrue(cause.getMessage().startsWith("The function " + FUNCTION + " failed: Error: function crashed"),
                cause.getMessage());
        assertEquals(response, exchange.getMessage().getHeader(GoogleCloudFunctionsConstants.RESPONSE_OBJECT));
    }

    @Test
    void aSuccessfulCallGivesTheResult() {
        CallFunctionResponse response = CallFunctionResponse.newBuilder().setExecutionId("exec-2").setResult("done").build();
        mockCloudFunctionsService.addResponse(response);

        Exchange exchange = template.request("direct:callFunction", e -> e.getIn().setBody("data"));

        assertNull(exchange.getException());
        assertEquals("done", exchange.getMessage().getBody());
        assertEquals(response, exchange.getMessage().getHeader(GoogleCloudFunctionsConstants.RESPONSE_OBJECT));
    }

    @Test
    void noBodyFailsWithAnInvalidPayloadException() {
        Exchange exchange = template.request("direct:callFunction", e -> e.getIn().setBody(null));

        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertEquals(String.class, cause.getType());
        assertTrue(mockCloudFunctionsService.getRequests().isEmpty());
    }

    @Test
    void aMapIsSentAsJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Camel");
        map.put("count", 2);

        assertEquals("{\"name\":\"Camel\",\"count\":2}", sentData(map));
    }

    @Test
    void aListIsSentAsJson() {
        assertEquals("[\"a\",\"b\"]", sentData(List.of("a", "b")));
    }

    @Test
    void aPojoIsSentAsJson() {
        assertEquals("{\"text\":\"Hello\",\"count\":2}", sentData(new Greeting("Hello", 2)));
    }

    @Test
    void aStringIsSentAsItIs() {
        assertEquals("{\"name\":\"Camel\"}", sentData("{\"name\":\"Camel\"}"));
    }

    @Test
    void bytesAreReadAsText() {
        assertEquals("Hello", sentData("Hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aStreamIsReadAsText() {
        assertEquals("Hello", sentData(new ByteArrayInputStream("Hello".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void aFileIsReadAsText() throws Exception {
        assertEquals("Hello", sentData(Files.writeString(dir.resolve("data.txt"), "Hello").toFile()));
    }

    @Test
    void aPathIsReadAsText() throws Exception {
        assertEquals("Hello", sentData(Files.writeString(dir.resolve("data.txt"), "Hello")));
    }

    @Test
    void theFileOfTheFileConsumerIsReadAsText() throws Exception {
        File file = Files.writeString(dir.resolve("data.txt"), "Hello").toFile();
        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());
        genericFile.setAbsoluteFilePath(file.getAbsolutePath());
        genericFile.setBody(file);
        genericFile.setBinding(new FileBinding());

        assertEquals("Hello", sentData(genericFile));
    }

    /**
     * Calls the function with the body, and gives the data of the request the function got.
     */
    private String sentData(Object body) {
        mockCloudFunctionsService.addResponse(CallFunctionResponse.newBuilder().setResult("done").build());

        Exchange exchange = template.request("direct:callFunction", e -> e.getIn().setBody(body));

        assertNull(exchange.getException());
        List<AbstractMessage> requests = mockCloudFunctionsService.getRequests();
        assertEquals(1, requests.size());
        CallFunctionRequest request = (CallFunctionRequest) requests.get(0);
        assertEquals(FUNCTION, request.getName());
        return request.getData();
    }

    record Greeting(String text, int count) {
    }
}
