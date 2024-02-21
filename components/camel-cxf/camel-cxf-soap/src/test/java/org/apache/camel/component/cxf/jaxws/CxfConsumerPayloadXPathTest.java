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
package org.apache.camel.component.cxf.jaxws;

import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.binding.soap.SoapHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfConsumerPayloadXPathTest extends CamelTestSupport {

    public static final String HEADER_SIZE = "tstsize";

    @Test
    public void size1XPathStringResultTest() throws Exception {
        simpleTest(1, new TestRouteWithXPathStringResultBuilder());
    }

    @Test
    public void size100XPathStringResultTest() throws Exception {
        simpleTest(100, new TestRouteWithXPathStringResultBuilder());
    }

    @Test
    public void size1000XPathStringResultTest() throws Exception {
        simpleTest(1000, new TestRouteWithXPathStringResultBuilder());
    }

    @Test
    public void size10000XPathStringResultTest() throws Exception {
        simpleTest(10000, new TestRouteWithXPathStringResultBuilder());
    }

    @Test
    public void size1XPathTest() throws Exception {
        simpleTest(1, new TestRouteWithXPathBuilder());
    }

    @Test
    public void size100XPathTest() throws Exception {
        simpleTest(100, new TestRouteWithXPathBuilder());
    }

    @Test
    public void size1000XPathTest() throws Exception {
        simpleTest(1000, new TestRouteWithXPathBuilder());
    }

    @Test
    public void size10000XPathTest() throws Exception {
        simpleTest(10000, new TestRouteWithXPathBuilder());
    }

    //the textnode appears to have siblings!
    @Test
    public void size10000DomTest() throws Exception {
        simpleTest(10000, new TestRouteWithDomBuilder());
    }

    @Test
    public void size1000DomFirstTest() throws Exception {
        simpleTest(1000, new TestRouteWithDomFirstOneOnlyBuilder());
    }

    private class TestRouteWithXPathBuilder extends BaseRouteBuilder {
        @Override
        public void configure() {
            from("cxf://" + testAddress + "?dataFormat=PAYLOAD")
                    .streamCaching()
                    .process(new XPathProcessor())
                    .process(new ResponseProcessor());
        }
    }

    private class TestRouteWithXPathStringResultBuilder extends BaseRouteBuilder {
        @Override
        public void configure() {
            from("cxf://" + testAddress + "?dataFormat=PAYLOAD")
                    .streamCaching()
                    .process(new XPathStringResultProcessor())
                    .process(new ResponseProcessor());
        }
    }

    private class TestRouteWithDomFirstOneOnlyBuilder extends BaseRouteBuilder {
        @Override
        public void configure() {
            from("cxf://" + testAddress + "?dataFormat=PAYLOAD")
                    .streamCaching()
                    .process(new DomFirstOneOnlyProcessor())
                    .process(new ResponseProcessor());
        }
    }

    private class TestRouteWithDomBuilder extends BaseRouteBuilder {
        @Override
        public void configure() {
            from("cxf://" + testAddress + "?dataFormat=PAYLOAD")
                    .streamCaching()
                    .process(new DomProcessor())
                    .process(new ResponseProcessor());
        }
    }

    //implementation simular to xpath() in route: no data loss
    private class XPathStringResultProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Object obj = exchange.getIn().getBody();
            //xpath expression directly results in a: String
            String content = (String) XPathBuilder.xpath("//xml/text()").stringResult().evaluate(context, obj, Object.class);
            exchange.getMessage().setBody(content);
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
        }
    }

    //this version leads to data loss
    private class XPathProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Object obj = exchange.getIn().getBody();
            //xpath expression results in a: net.sf.saxon.dom.DOMNodeList
            //after which it is converted to a String
            String content = XPathBuilder.xpath("//xml/text()").evaluate(context, obj, String.class);
            exchange.getMessage().setBody(content);
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
        }
    }

    //this version leads to data loss
    private class DomFirstOneOnlyProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Object obj = exchange.getIn().getBody();
            @SuppressWarnings("unchecked")
            CxfPayload<SoapHeader> payload = (CxfPayload<SoapHeader>) obj;
            Element el = payload.getBody().get(0);
            Text textnode = (Text) el.getFirstChild();
            exchange.getMessage().setBody(textnode.getNodeValue());
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
        }
    }

    private class DomProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Object obj = exchange.getIn().getBody();
            @SuppressWarnings("unchecked")
            CxfPayload<SoapHeader> payload = (CxfPayload<SoapHeader>) obj;
            Element el = payload.getBody().get(0);
            Text textnode = (Text) el.getFirstChild();

            StringBuilder b = new StringBuilder();
            b.append(textnode.getNodeValue());
            textnode = (Text) textnode.getNextSibling();
            while (textnode != null) {
                //the textnode appears to have siblings!
                b.append(textnode.getNodeValue());
                textnode = (Text) textnode.getNextSibling();
            }

            exchange.getMessage().setBody(b.toString());
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
        }
    }

    private class ResponseProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Object obj = exchange.getIn().getBody();
            String content = (String) obj;
            String msgOut = constructSoapMessage(content);
            exchange.getMessage().setBody(msgOut);
            exchange.getMessage().setHeaders(exchange.getIn().getHeaders());
            exchange.getMessage().setHeader(HEADER_SIZE, "" + content.length());
        }
    }

    private void simpleTest(int repeat, BaseRouteBuilder builder) throws Exception {
        setUseRouteBuilder(false);
        context.addRoutes(builder);
        startCamelContext();

        String content = StringUtils.repeat("x", repeat);
        String msgIn = constructSoapMessage(content);

        Exchange exchgIn = new DefaultExchange(context);
        exchgIn.setPattern(ExchangePattern.InOut);
        exchgIn.getIn().setBody(msgIn);

        //Execute
        Exchange exchgOut = template.send(builder.getTestAddress(), exchgIn);

        //Verify
        String result = exchgOut.getMessage().getBody(String.class);
        assertNotNull(result, "response on http call");

        //check for data loss in received input (after xpath)
        String headerSize = exchgOut.getMessage().getHeader(HEADER_SIZE, String.class);
        assertEquals(Integer.toString(repeat), headerSize);

        assertTrue(result.length() > repeat, "dataloss in output occurred");

        stopCamelContext();
    }

    private abstract class BaseRouteBuilder extends RouteBuilder {
        protected final String testAddress = getAvailableUrl("test");

        public String getTestAddress() {
            return testAddress;
        }
    }

    private String getAvailableUrl(String pathEnd) {
        int availablePort = AvailablePortFinder.getNextAvailable();
        String url = "http://localhost:" + availablePort
                     + "/" + getClass().getSimpleName();
        return url + "/" + pathEnd;
    }

    private String constructSoapMessage(String content) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
               + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
               + "<soapenv:Body><xml>" + content + "</xml></soapenv:Body>"
               + "</soapenv:Envelope>";
    }
}
