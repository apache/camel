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
package org.apache.camel.processor.transformer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultTransformerRegistry;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class StringDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final StringDataTypeTransformer transformer = new StringDataTypeTransformer();

    @Test
    void shouldRetainStringModel() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader("file", "test.txt");
        exchange.getMessage().setBody("Test");
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertTrue(exchange.getMessage().hasHeaders());
        assertStringBody(exchange, "test.txt", "Test");
    }

    @Test
    void shouldMapFromBinaryToStringModel() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader("file", "test1.txt");
        exchange.getMessage().setBody("Test1".getBytes(StandardCharsets.UTF_8));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertTrue(exchange.getMessage().hasHeaders());
        assertStringBody(exchange, "test1.txt", "Test1");
    }

    @Test
    void shouldMapFromInputStreamToStringModel() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setHeader("file", "test3.txt");
        exchange.getMessage().setBody(new ByteArrayInputStream("Test3".getBytes(StandardCharsets.UTF_8)));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertTrue(exchange.getMessage().hasHeaders());
        assertStringBody(exchange, "test3.txt", "Test3");
    }

    @Test
    public void shouldLookupDataType() {
        DefaultTransformerRegistry dataTypeRegistry = new DefaultTransformerRegistry(camelContext);
        Transformer transformer = dataTypeRegistry.resolveTransformer(new TransformerKey("text-plain"));
        Assertions.assertNotNull(transformer);

        transformer = dataTypeRegistry.resolveTransformer(new TransformerKey("text/plain"));
        Assertions.assertNotNull(transformer);

        transformer = dataTypeRegistry.resolveTransformer(new TransformerKey("camel:text-plain"));
        Assertions.assertNotNull(transformer);
    }

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body Camel converts, as transformerIn says, and the body transformerOut says
        BodyTypes bodyTypes = StringDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertEquals(Carrier.ANY, bodyTypes.transformerIn().value()[0]);
        assertEquals(Carrier.TEXT, bodyTypes.transformerOut().value()[0]);
        assertEquals(String.class.getCanonicalName(), bodyTypes.transformerOut().javaType());
        for (Object body : List.of("Test", "Test".getBytes(StandardCharsets.UTF_8),
                new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)), 5)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertInstanceOf(String.class, exchange.getMessage().getBody(), body.getClass().getName());
        }
    }

    private static void assertStringBody(Exchange exchange, String key, String content) {
        assertEquals(key, exchange.getMessage().getHeader("file"));

        assertEquals(String.class, exchange.getMessage().getBody().getClass());
        assertEquals(content, exchange.getMessage().getBody(String.class));
    }
}
