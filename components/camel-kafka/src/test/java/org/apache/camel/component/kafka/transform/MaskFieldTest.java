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
package org.apache.camel.component.kafka.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaskFieldTest {

    private DefaultCamelContext camelContext;

    private final ObjectMapper mapper = new ObjectMapper();

    private MaskField processor;

    private final String baseJson = "{" + "\n" +
                                    "  \"name\" : \"Rajesh Koothrappali\"" + "\n" +
                                    "}";

    @BeforeEach
    void setup() {
        camelContext = new DefaultCamelContext();
        processor = new MaskField();
    }

    @Test
    void shouldMaskField() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        JsonNode s = processor.process("name", "xxxx", exchange);
        Assertions.assertEquals("\"xxxx\"", s.get("name").toString());
    }

    @Test
    void shouldMaskFieldWithNull() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        JsonNode s = processor.process("name", null, exchange);
        Assertions.assertEquals("\"\"", s.get("name").toString());
    }

    @Test
    void shouldMaskFieldList() throws Exception {
        Map<String, List<String>> names = new HashMap<>();
        Exchange exchange = new DefaultExchange(camelContext);
        List<String> els = new ArrayList<>();
        els.add("Sheldon");
        els.add("Rajesh");
        els.add("Leonard");
        names.put("names", els);

        exchange.getMessage().setBody(mapper.writeValueAsString(names));

        JsonNode s = processor.process("names", null, exchange);
        Assertions.assertEquals("[]", s.get("names").toString());
    }
}
