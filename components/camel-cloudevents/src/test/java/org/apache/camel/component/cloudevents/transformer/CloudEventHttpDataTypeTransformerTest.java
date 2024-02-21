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

class CloudEventHttpDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final CloudEventHttpDataTypeTransformer transformer = new CloudEventHttpDataTypeTransformer();

    @Test
    void shouldMapToHttpCloudEvent() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT, "test1.txt");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE, "org.apache.camel.event.test");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "org.apache.camel.test");
        exchange.getMessage().setHeader(CloudEvent.CAMEL_CLOUD_EVENT_CONTENT_TYPE, "text/plain");
        exchange.getMessage().setBody(new ByteArrayInputStream("Test1".getBytes(StandardCharsets.UTF_8)));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        CloudEvent cloudEvent = CloudEvents.v1_0;
        assertTrue(exchange.getMessage().hasHeaders());
        assertEquals(exchange.getExchangeId(),
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).http()));
        assertEquals(cloudEvent.version(),
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_VERSION).http()));
        assertEquals("org.apache.camel.event.test",
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).http()));
        assertEquals("test1.txt",
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT).http()));
        assertEquals("org.apache.camel.test",
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).http()));
        assertTrue(exchange.getMessage().getHeaders()
                .containsKey(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TIME).http()));
        assertEquals("text/plain", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("Test1", exchange.getMessage().getBody(String.class));

        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
        assertNull(exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    void shouldSetDefaultCloudEventAttributes() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        CloudEvent cloudEvent = CloudEvents.v1_0;
        assertTrue(exchange.getMessage().hasHeaders());
        assertEquals(exchange.getExchangeId(),
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_ID).http()));
        assertEquals(cloudEvent.version(),
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_VERSION).http()));
        assertEquals(CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_TYPE,
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TYPE).http()));
        assertNull(exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT).http()));
        assertEquals(CloudEvent.DEFAULT_CAMEL_CLOUD_EVENT_SOURCE,
                exchange.getMessage().getHeader(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE).http()));
        assertTrue(exchange.getMessage().getHeaders()
                .containsKey(cloudEvent.mandatoryAttribute(CloudEvent.CAMEL_CLOUD_EVENT_TIME).http()));
        assertEquals("application/json", exchange.getMessage().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("{}", exchange.getMessage().getBody(String.class));
    }

    @Test
    public void shouldLookupTransformer() throws Exception {
        Transformer transformer
                = camelContext.getTransformerRegistry().resolveTransformer(new TransformerKey("http:application-cloudevents"));
        Assertions.assertNotNull(transformer);
        Assertions.assertEquals(CloudEventHttpDataTypeTransformer.class, transformer.getClass());
    }
}
