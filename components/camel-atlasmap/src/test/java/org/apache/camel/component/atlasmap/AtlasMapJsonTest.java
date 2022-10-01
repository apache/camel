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
package org.apache.camel.component.atlasmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AtlasMapJsonTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("context/AtlasMapJsonTest-context.xml");
    }

    @Test
    public void testMocksAreValid() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody("direct:start", Util.generateMockTwitterStatus());

        MockEndpoint.assertIsSatisfied(context);
        Object body = result.getExchanges().get(0).getIn().getBody();
        assertEquals(String.class, body.getClass());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode outJson = mapper.readTree((String) body);
        assertEquals("Bob", outJson.get("FirstName").asText());
        assertEquals("Vila", outJson.get("LastName").asText());
        assertEquals("bobvila1982", outJson.get("Title").asText());
        assertEquals("Let's build a house!", outJson.get("Description").asText());
    }

}
