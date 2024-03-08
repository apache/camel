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
import org.apache.camel.component.cloudevents.CloudEvent;
import org.apache.camel.component.cloudevents.CloudEvents;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("application/cloudevents+json", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
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
    void shouldSetDefaultCloudEventAttributes() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        CloudEvent cloudEvent = CloudEvents.v1_0;
        assertTrue(exchange.getMessage().hasHeaders());
        assertEquals("application/cloudevents+json", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
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
    public void shouldLookupTransformer() throws Exception {
        Transformer transformer
                = camelContext.getTransformerRegistry().resolveTransformer(new TransformerKey("application-cloudevents+json"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(CloudEventJsonDataTypeTransformer.class, transformer.getClass());
    }
}
