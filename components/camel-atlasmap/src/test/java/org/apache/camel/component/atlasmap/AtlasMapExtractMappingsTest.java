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

import java.io.ByteArrayInputStream;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AtlasMapExtractMappingsTest extends CamelSpringTestSupport {

    private static final String EXPECTED_BODY = "{\"order\":{\"orderId\":\"A123\"}}";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("context/AtlasMapExtractMappingsTest-context.xml");
    }

    @Test
    public void testXMLMappingsExtraction() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody("direct:start",
                new ByteArrayInputStream("{ \"order\": { \"orderId\": \"A123\" }}".getBytes()));

        MockEndpoint.assertIsSatisfied(context);

        final Object body = result.getExchanges().get(0).getIn().getBody();
        assertEquals(EXPECTED_BODY, body);
    }

    @Test
    @DirtiesContext
    public void testXMLMappingsExtractionNumberedMappingFile() throws Exception {
        MockEndpoint resultN = getMockEndpoint("mock:result-n");
        resultN.setExpectedCount(1);

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody("direct:start-n",
                new ByteArrayInputStream("{ \"order\": { \"orderId\": \"A123\" }}".getBytes()));

        MockEndpoint.assertIsSatisfied(context);

        final Object body = resultN.getExchanges().get(0).getIn().getBody();
        assertEquals(EXPECTED_BODY, body);
    }

}
