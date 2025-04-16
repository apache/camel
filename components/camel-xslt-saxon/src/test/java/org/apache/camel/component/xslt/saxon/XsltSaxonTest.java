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
package org.apache.camel.component.xslt.saxon;

import java.util.List;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltSaxonTest extends CamelTestSupport {

    @Test
    public void testSendStringMessage() throws Exception {
        sendMessageAndHaveItTransformed("<mail><subject>Hey</subject><body>Hello world!</body></mail>");
    }

    @Test
    public void testSendBytesMessage() throws Exception {
        sendMessageAndHaveItTransformed("<mail><subject>Hey</subject><body>Hello world!</body></mail>".getBytes());
    }

    @Test
    public void testSendDomMessage() throws Exception {
        XmlConverter converter = new XmlConverter();
        Document body = converter.toDOMDocument("<mail><subject>Hey</subject><body>Hello world!</body></mail>", null);
        sendMessageAndHaveItTransformed(body);
    }

    @Test
    public void testSendStringMessageSourceHeader() throws Exception {
        sendMessageAndHaveItTransformedFromHeader("<mail><subject>Hey</subject><body>Hello world!</body></mail>");
    }

    @Test
    public void testSendBytesMessageSourceHeader() throws Exception {
        sendMessageAndHaveItTransformedFromHeader("<mail><subject>Hey</subject><body>Hello world!</body></mail>".getBytes());
    }

    @Test
    public void testSendDomMessageSourceHeader() throws Exception {
        XmlConverter converter = new XmlConverter();
        Document body = converter.toDOMDocument("<mail><subject>Hey</subject><body>Hello world!</body></mail>", null);
        sendMessageAndHaveItTransformedFromHeader(body);
    }

    @Test
    public void testSendStringMessageSourceVariable() throws Exception {
        sendMessageAndHaveItTransformedFromVariable("<mail><subject>Hey</subject><body>Hello world!</body></mail>");
    }

    @Test
    public void testSendBytesMessageSourceVariable() throws Exception {
        sendMessageAndHaveItTransformedFromVariable("<mail><subject>Hey</subject><body>Hello world!</body></mail>".getBytes());
    }

    @Test
    public void testSendDomMessageSourceVariable() throws Exception {
        XmlConverter converter = new XmlConverter();
        Document body = converter.toDOMDocument("<mail><subject>Hey</subject><body>Hello world!</body></mail>", null);
        sendMessageAndHaveItTransformedFromVariable(body);
    }

    @Test
    public void testSendStringMessageSourceProperty() throws Exception {
        sendMessageAndHaveItTransformedFromProperty("<mail><subject>Hey</subject><body>Hello world!</body></mail>");
    }

    @Test
    public void testSendBytesMessageSourceProperty() throws Exception {
        sendMessageAndHaveItTransformedFromProperty("<mail><subject>Hey</subject><body>Hello world!</body></mail>".getBytes());
    }

    @Test
    public void testSendDomMessageSourceProperty() throws Exception {
        XmlConverter converter = new XmlConverter();
        Document body = converter.toDOMDocument("<mail><subject>Hey</subject><body>Hello world!</body></mail>", null);
        sendMessageAndHaveItTransformedFromProperty(body);
    }

    private void sendMessageAndHaveItTransformed(Object body) throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull("The transformed XML should not be null", xml);
        assertTrue(xml.indexOf("transformed") > -1);
        // the cheese tag is in the transform.xsl
        assertTrue(xml.indexOf("cheese") > -1);
        assertTrue(xml.indexOf("<subject>Hey</subject>") > -1);
        assertTrue(xml.indexOf("<body>Hello world!</body>") > -1);
    }

    private void sendMessageAndHaveItTransformedFromHeader(Object body) throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:sourceHeader");
        endpoint.expectedMessageCount(1);

        template.send("direct:sourceHeader", exchange -> {
            exchange.getIn().setHeader("xmlSource", body);
        });

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull("The transformed XML should not be null", xml);
        assertTrue(xml.indexOf("transformed") > -1);
        // the cheese tag is in the transform.xsl
        assertTrue(xml.indexOf("cheese") > -1);
        assertTrue(xml.indexOf("<subject>Hey</subject>") > -1);
        assertTrue(xml.indexOf("<body>Hello world!</body>") > -1);
    }

    private void sendMessageAndHaveItTransformedFromVariable(Object body) throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:sourceVariable");
        endpoint.expectedMessageCount(1);

        template.send("direct:sourceVariable", exchange -> {
            exchange.setVariable("xmlSource", body);
        });

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull("The transformed XML should not be null", xml);
        assertTrue(xml.indexOf("transformed") > -1);
        // the cheese tag is in the transform.xsl
        assertTrue(xml.indexOf("cheese") > -1);
        assertTrue(xml.indexOf("<subject>Hey</subject>") > -1);
        assertTrue(xml.indexOf("<body>Hello world!</body>") > -1);
    }

    private void sendMessageAndHaveItTransformedFromProperty(Object body) throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:sourceProperty");
        endpoint.expectedMessageCount(1);

        template.send("direct:sourceProperty", exchange -> {
            exchange.setProperty("xmlSource", body);
        });

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull("The transformed XML should not be null", xml);
        assertTrue(xml.indexOf("transformed") > -1);
        // the cheese tag is in the transform.xsl
        assertTrue(xml.indexOf("cheese") > -1);
        assertTrue(xml.indexOf("<subject>Hey</subject>") > -1);
        assertTrue(xml.indexOf("<body>Hello world!</body>") > -1);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("xslt-saxon:xslt/transform.xsl")
                        .to("mock:result");

                from("direct:sourceHeader")
                        .to("xslt-saxon:xslt/transform.xsl?source=header:xmlSource")
                        .to("mock:sourceHeader");

                from("direct:sourceVariable")
                        .to("xslt-saxon:classpath:xslt/transform.xsl?source=variable:xmlSource")
                        .to("mock:sourceVariable");

                from("direct:sourceProperty")
                        .to("xslt-saxon:classpath:xslt/transform.xsl?source=property:xmlSource")
                        .to("mock:sourceProperty");

            }
        };
    }

}
