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
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AtlasMapXmlToXmlDefaultNsTest extends CamelSpringTestSupport {

    private static final String XML_EXPECTED
            = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><tns:Patient xmlns:tns=\"http://hl7.org/fhir\"><tns:id value=\"101138\"/></tns:Patient>";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("context/AtlasMapXmlToXmlTest-context.xml");
    }

    @Test
    public void testMocksAreValid() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        final ProducerTemplate producerTemplate = context.createProducerTemplate();
        producerTemplate.sendBody("direct:start", new ByteArrayInputStream(
                "<Patient xmlns=\"http://hl7.org/fhir\"><id value=\"101138\"></id></Patient>".getBytes()));

        MockEndpoint.assertIsSatisfied(context);
        final String body = result.getExchanges().get(0).getIn().getBody(String.class);

        assertNotNull(body);

        Diff d = DiffBuilder.compare(Input.fromString(XML_EXPECTED).build())
                .withTest(Input.fromString(body).build())
                .ignoreWhitespace().build();
        assertFalse(d.hasDifferences(), d.toString() + ": " + body);
    }

}
