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
package org.apache.camel.component.jms.issues;

import java.io.File;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test based on user forum issue.
 */
public class JmsTypeConverterIssueTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Test
    public void testJmsTypeConverterIssue() throws Exception {
        String body = "<?xml version=\"1.0\"?><portal agent=\"123\"><id>456</id><name>Cool Portal</name></portal>";

        // create local file
        deleteDirectory("target/files");
        template.sendBodyAndHeader("file://target/files/123", body, Exchange.FILE_NAME, "agent.xml");

        MockEndpoint filter = getMockEndpoint("mock:filterxml");
        filter.expectedMessageCount(1);
        filter.message(0).body().isInstanceOf(Document.class);

        getMockEndpoint("mock:portalxml").expectedMessageCount(1);
        getMockEndpoint("mock:historyxml").expectedMessageCount(1);

        template.sendBody("activemq:queue:inbox", "<?xml version=\"1.0\"?><agent id=\"123\"></agent>");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:inbox")
                        .setProperty(Exchange.CHARSET_NAME, constant("UTF-8"))
                        .setHeader("agentId", xpath("/agent/@id"))
                        .process(new FixateHeaderValuesProcessor())
                        .setHeader(Exchange.FILE_NAME, simple("target/files/${in.header.agentId}/agent.xml"))
                        .process(new ReadLocalFile())
                        .to("direct:filterxml")
                        .multicast().to("direct:portalxml", "direct:historyxml");

                from("direct:filterxml")
                        .process(new FilterProcessor())
                        .to("mock:filterxml");

                from("direct:portalxml")
                        .to("mock:portalxml");

                from("direct:historyxml")
                        .to("mock:historyxml");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }

    private static class FixateHeaderValuesProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            String id = exchange.getIn().getHeader("agentId", String.class);
            exchange.getIn().setHeader("agentId", id);
        }
    }

    private static class ReadLocalFile implements Processor {
        @Override
        public void process(Exchange exchange) {
            String filename = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            exchange.getIn().setBody(new File(filename));
        }
    }

    private static class FilterProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Document document = exchange.getIn().getBody(Document.class);
            assertNotNull(document, "Should be able to convert to XML Document");

            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();

            String id = exchange.getIn().getHeader("agentId", String.class);
            String expr = "//portal[/portal/@agent = '" + id + "']";

            NodeList nodes = (NodeList) xpath.compile(expr).evaluate(document, XPathConstants.NODESET);
            assertNotNull(nodes, "Should be able to do xpath");
            assertEquals(1, nodes.getLength());

            String portalId = nodes.item(0).getFirstChild().getTextContent();
            String portalName = nodes.item(0).getLastChild().getTextContent();

            assertEquals("456", portalId);
            assertEquals("Cool Portal", portalName);

            exchange.getIn().setHeader("portalId", portalId);
            exchange.getIn().setBody(document);
        }
    }

}
