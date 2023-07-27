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

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataTypeProcessorTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final DataTypeProcessor processor = new DataTypeProcessor();

    @BeforeEach
    void setup() {
        CamelContextAware.trySetCamelContext(processor, camelContext);
    }

    @Test
    public void shouldApplyDataTypeConverterFromAnnotationLookup() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)));
        processor.setToType("uppercase");
        processor.process(exchange);

        assertEquals(String.class, exchange.getMessage().getBody().getClass());
        assertEquals("TEST", exchange.getMessage().getBody());
    }

    @Test
    public void shouldApplyDataTypeConverterFromResourceLookup() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)));
        processor.setToType("lowercase");
        processor.process(exchange);

        assertEquals(String.class, exchange.getMessage().getBody().getClass());
        assertEquals("test", exchange.getMessage().getBody());
    }

    @Test
    public void shouldIgnoreUnknownDataType() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(new ByteArrayInputStream("Test".getBytes(StandardCharsets.UTF_8)));
        processor.setIgnoreMissingDataType(true);
        processor.setToType("foo:unknown");
        processor.process(exchange);

        assertEquals(ByteArrayInputStream.class, exchange.getMessage().getBody().getClass());
        assertEquals("Test", exchange.getMessage().getBody(String.class));
    }
}
