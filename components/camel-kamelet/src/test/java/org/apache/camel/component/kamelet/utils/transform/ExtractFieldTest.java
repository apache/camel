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
package org.apache.camel.component.kamelet.utils.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtractFieldTest {

    private DefaultCamelContext camelContext;

    private final ObjectMapper mapper = new ObjectMapper();

    private ExtractField processor;

    private final String baseJson = "{" + "\n" +
                                    "  \"name\" : \"Rajesh Koothrappali\"" + "\n" +
                                    "}";

    @BeforeEach
    void setup() {
        camelContext = new DefaultCamelContext();
        processor = new ExtractField();
    }

    @Test
    void shouldExtractFieldFromJsonNode() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        processor.setField("name");
        processor.process(exchange);

        Assertions.assertEquals("Rajesh Koothrappali", exchange.getMessage().getBody(String.class));
    }

    @Test
    void shouldExtractFieldToHeader() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        processor.setField("name");
        processor.setHeaderOutput(true);
        processor.setHeaderOutputName("name");
        processor.process(exchange);

        Assertions.assertEquals(baseJson, exchange.getMessage().getBody(String.class));
        Assertions.assertEquals("Rajesh Koothrappali", exchange.getMessage().getHeader("name"));
    }

    @Test
    void shouldExtractFieldToHeaderWithStrictHeaderCheck() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        processor.setField("name");
        processor.setHeaderOutput(true);
        processor.setHeaderOutputName("name");
        processor.setStrictHeaderCheck(true);
        processor.process(exchange);

        Assertions.assertEquals(baseJson, exchange.getMessage().getBody(String.class));
        Assertions.assertEquals("Rajesh Koothrappali", exchange.getMessage().getHeader("name"));

        exchange.getMessage().setHeader("name", "somethingElse");

        processor.process(exchange);

        Assertions.assertEquals("Rajesh Koothrappali", exchange.getMessage().getBody(String.class));
        Assertions.assertEquals("somethingElse", exchange.getMessage().getHeader("name"));
    }

    @Test
    void shouldExtractFieldToDefaultHeader() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        processor.setField("name");
        processor.setHeaderOutput(true);
        processor.process(exchange);

        Assertions.assertEquals(baseJson, exchange.getMessage().getBody(String.class));
        Assertions.assertEquals("Rajesh Koothrappali", exchange.getMessage().getHeader(ExtractField.EXTRACTED_FIELD_HEADER));

        exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        processor.setHeaderOutputName("none");
        processor.process(exchange);

        Assertions.assertEquals(baseJson, exchange.getMessage().getBody(String.class));
        Assertions.assertEquals("Rajesh Koothrappali", exchange.getMessage().getHeader(ExtractField.EXTRACTED_FIELD_HEADER));
    }

    @Test
    void shouldExtractFieldWithT() throws Exception {
        final String baseJson = "{\"id\":\"1\",\"message\":\"Camel\\\\tRocks\"}";
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        processor.setField("message");
        processor.setTrimField(true);
        processor.process(exchange);

        Assertions.assertEquals("Camel\\tRocks", exchange.getMessage().getBody());
    }

}
