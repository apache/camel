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

package org.apache.camel.component.cloudevents.transformer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.cloudevents.CloudEvents;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudEventJsonDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final CloudEventJsonDataTypeTransformer transformer = new CloudEventJsonDataTypeTransformer();

    @Test
    void shouldMapToJsonCloudEventFormat() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, "test1.txt");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event.test");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "org.apache.camel.test");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "text/plain");
        exchange.getMessage().setBody(new ByteArrayInputStream("Test1".getBytes(StandardCharsets.UTF_8)));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        CloudEvent cloudEvent = CloudEvents.v1_0;
        assertTrue(exchange.getMessage().hasHeaders());
        assertEquals(CloudEventJsonDataTypeTransformer.APPLICATION_CLOUDEVENTS_JSON,
                exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"%s\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).json(), exchange.getExchangeId())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"org.apache.camel.event.test\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"org.apache.camel.test\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"text/plain\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains("\"data\":\"Test1\""));

        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    void shouldHandleJsonBody() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, "test1.txt");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event.test");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "org.apache.camel.test");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "application/json");
        exchange.getMessage().setBody(new ByteArrayInputStream("""
                {
                    "message": "Test1"
                }
                """.getBytes(StandardCharsets.UTF_8)));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        CloudEvent cloudEvent = CloudEvents.v1_0;
        assertTrue(exchange.getMessage().hasHeaders());
        assertEquals(CloudEventJsonDataTypeTransformer.APPLICATION_CLOUDEVENTS_JSON,
                exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"%s\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).json(), exchange.getExchangeId())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"org.apache.camel.event.test\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"org.apache.camel.test\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"application/json\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains("\"data\":{\n"));

        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    void shouldSetDefaultCloudEventAttributes() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        CloudEvent cloudEvent = CloudEvents.v1_0;
        assertTrue(exchange.getMessage().hasHeaders());
        assertEquals(CloudEventJsonDataTypeTransformer.APPLICATION_CLOUDEVENTS_JSON,
                exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"%s\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).json(), exchange.getExchangeId())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"org.apache.camel.event\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"org.apache.camel\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains(String.format("\"%s\":\"application/json\"",
                cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json())));
        assertTrue(exchange.getMessage().getBody(String.class).contains("\"data\":\"Test\""));
    }

    @Test
    void shouldEscapeTextData() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        String text = "He said \"hi\"\nC:\\temp\\new\tdone\u0001";
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "text/plain");
        exchange.getMessage().setBody(text);

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        String json = exchange.getMessage().getBody(String.class);
        // JSON strings must not contain raw control characters (Jsoner would accept them)
        assertTrue(json.chars().noneMatch(ch -> ch < 0x20), json);
        assertTrue(json.contains("\\n") && json.contains("\\t") && json.contains("\\u0001"), json);
        JsonObject event = (JsonObject) Jsoner.deserialize(json);
        assertEquals(text, event.getString("data"));
        assertEquals("text/plain", event.getString(
                CloudEvents.v1_0.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_DATA_CONTENT_TYPE).json()));
    }

    @Test
    void shouldKeepTextDataThatOnlyLooksLikeJson() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        String text = "[INFO] order 42 received";
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "text/plain");
        exchange.getMessage().setBody(text);

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JsonObject event = (JsonObject) Jsoner.deserialize(exchange.getMessage().getBody(String.class));
        assertEquals(text, event.getString("data"));
    }

    @Test
    void shouldEscapeAttributeValues() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        String subject = "reports\\2026 \"Q3\".csv";
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, subject);
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "text/plain");
        exchange.getMessage().setBody("Test");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JsonObject event = (JsonObject) Jsoner.deserialize(exchange.getMessage().getBody(String.class));
        assertEquals(subject,
                event.getString(CloudEvents.v1_0.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT).json()));
        assertEquals("Test", event.getString("data"));
    }

    @Test
    void shouldNestJsonData() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "application/json");
        exchange.getMessage().setBody("{\"message\": \"He said \\\"hi\\\"\", \"items\": [1, 2]}");

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JsonObject event = (JsonObject) Jsoner.deserialize(exchange.getMessage().getBody(String.class));
        JsonObject data = (JsonObject) event.get("data");
        assertEquals("He said \"hi\"", data.getString("message"));
        assertEquals(2, data.getCollection("items").size());
    }

    @Test
    void shouldKeepMalformedJsonDataAsText() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        // declared as Json, but cut short: nesting it would make the event invalid Json
        String text = "{\"message\": \"Test1\", \"items\": [1, 2";
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "application/json");
        exchange.getMessage().setBody(text);

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        JsonObject event = (JsonObject) Jsoner.deserialize(exchange.getMessage().getBody(String.class));
        assertEquals(text, event.getString("data"));
    }

    @Test
    void shouldRecognizeJsonObjectsAndArrays() {
        assertTrue(CloudEventJsonDataTypeTransformer.isJson("{}"));
        assertTrue(CloudEventJsonDataTypeTransformer.isJson("[]"));
        assertTrue(CloudEventJsonDataTypeTransformer.isJson(" \r\n\t{ } \n"));
        assertTrue(CloudEventJsonDataTypeTransformer.isJson("[{\"a\":{\"b\":[[], {}]}}, \"\"]"));
        assertTrue(CloudEventJsonDataTypeTransformer.isJson(
                "{\"a\": [0, -1, 12.5, -0.25e10, 1E+2, 3e-4, true, false, null], \"s\": \"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u00e9\u00e9\"}"));
        // deeper than the initial nesting stack
        assertTrue(CloudEventJsonDataTypeTransformer.isJson("[".repeat(100) + "{\"a\":1}" + "]".repeat(100)));
    }

    @Test
    void shouldRejectTextThatIsNotJsonObjectOrArray() {
        for (String text : new String[] {
                null, "", "  ", "Test", "\"text\"", "42", "true", "null",
                "[INFO] order 42 received", "{ \"a\": 1 } trailing", "[1] [2]",
                "{", "[", "{\"a\": 1", "[1, 2", "{\"a\": 1]", "[1}",
                "[1,]", "{\"a\": 1,}", "[,1]", "{,}", "[1 2]", "{\"a\" 1}", "{\"a\": 1 \"b\": 2}", "{a: 1}", "{'a': 1}",
                "{\"a\"}", "{\"a\":}", "[01]", "[1.]", "[.5]", "[-]", "[1e]", "[1e+]", "[+1]", "[0x1F]",
                "[tru]", "[nul]", "[True]", "[NaN]",
                "[\"open]", "[\"a\\x\"]", "[\"a\\u12g4\"]", "[\"a\\u12\"]", "[\"a\\\"]",
                "[\"raw\u0001control\"]", "[\"raw\nnew line\"]", "\u000b{}" }) {
            assertFalse(CloudEventJsonDataTypeTransformer.isJson(text), text);
        }
    }

    @Test
    public void shouldLookupTransformer() throws Exception {
        Transformer transformer
                = camelContext.getTransformerRegistry().resolveTransformer(new TransformerKey("application-cloudevents+json"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(CloudEventJsonDataTypeTransformer.class, transformer.getClass());

        transformer
                = camelContext.getTransformerRegistry()
                        .resolveTransformer(new TransformerKey(CloudEventJsonDataTypeTransformer.APPLICATION_CLOUDEVENTS_JSON));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(CloudEventJsonDataTypeTransformer.class, transformer.getClass());
    }
}
