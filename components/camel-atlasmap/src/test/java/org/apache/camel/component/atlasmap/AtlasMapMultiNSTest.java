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

import io.atlasmap.java.test.SourceContact;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

public class AtlasMapMultiNSTest extends CamelSpringTestSupport {

    private static final String XML_EXPECTED = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                               "<tns:request xmlns:tns=\"http://syndesis.io/v1/swagger-connector-template/request\">\n"
                                               +
                                               "  <tns:body>\n" +
                                               "    <Pet>\n" +
                                               "      <name>Jackson</name>\n" +
                                               "    </Pet>\n" +
                                               "  </tns:body>\n" +
                                               "</tns:request>";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("context/AtlasMapMultiNSTest-context.xml");
    }

    @Test
    public void test() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        ProducerTemplate producerTemplate = context.createProducerTemplate();
        SourceContact c = new SourceContact();
        c.setFirstName("Jackson");
        producerTemplate.sendBody("direct:start", c);

        MockEndpoint.assertIsSatisfied(context);
        Message message = result.getExchanges().get(0).getIn();
        Assertions.assertEquals("application/xml", message.getHeader(Exchange.CONTENT_TYPE));
        String out = message.getBody(String.class);
        Assertions.assertNotNull(out);
        Diff d = DiffBuilder.compare(Input.fromString(XML_EXPECTED).build())
                .withTest(Input.fromString(out).build())
                .ignoreWhitespace().build();
        Assertions.assertFalse(d.hasDifferences(), d.toString() + ": " + out);
    }

}
