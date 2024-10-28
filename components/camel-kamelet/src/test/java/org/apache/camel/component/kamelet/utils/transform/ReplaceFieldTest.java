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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplaceFieldTest {

    private DefaultCamelContext camelContext;

    private final ObjectMapper mapper = new ObjectMapper();

    private ReplaceField processor;

    private final String baseJson = "{" + "\n" +
                                    "  \"name\" : \"Rajesh Koothrappali\"," + "\n" +
                                    "  \"age\" : \"29\"" + "\n" +
                                    "}";

    @BeforeEach
    void setup() {
        camelContext = new DefaultCamelContext();
        processor = new ReplaceField();
    }

    @Test
    void shouldReplaceFieldToPlainJson() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        JsonNode node = processor.process("all", "none", "name:firstName,age:years", exchange);

        Assertions.assertEquals(node.toString(), "{" +
                                                 "\"firstName\":\"Rajesh Koothrappali\"," +
                                                 "\"years\":\"29\"" +
                                                 "}");
    }

    @Test
    void shouldReplaceFieldWithSpecificRename() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        JsonNode node = processor.process("name,age", "none", "name:firstName", exchange);

        Assertions.assertEquals(node.toString(), "{" +
                                                 "\"firstName\":\"Rajesh Koothrappali\"," +
                                                 "\"age\":\"29\"" +
                                                 "}");
    }

    @Test
    void shouldReplaceFieldWithSpecificRenameAndDisableFields() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        JsonNode node = processor.process("name", "none", "name:firstName", exchange);

        Assertions.assertEquals(node.toString(), "{" +
                                                 "\"firstName\":\"Rajesh Koothrappali\"" +
                                                 "}");
    }

    @Test
    void shouldReplaceFieldWithSpecificDisableFields() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        JsonNode node = processor.process("all", "name,age", "name:firstName", exchange);

        Assertions.assertEquals(node.toString(), "{" +
                                                 "\"name\":\"Rajesh Koothrappali\"," +
                                                 "\"age\":\"29\"" +
                                                 "}");
    }

    @Test
    void shouldReplaceFieldWithDisableAllFields() throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);

        exchange.getMessage().setBody(mapper.readTree(baseJson));

        JsonNode node = processor.process("none", "all", "name:firstName", exchange);

        Assertions.assertEquals(node.toString(), "{" +
                                                 "\"name\":\"Rajesh Koothrappali\"," +
                                                 "\"age\":\"29\"" +
                                                 "}");
    }
}
