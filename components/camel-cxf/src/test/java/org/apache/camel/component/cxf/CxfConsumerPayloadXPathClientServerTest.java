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
package org.apache.camel.component.cxf;

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.BusFactory;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.junit.Test;

public class CxfConsumerPayloadXPathClientServerTest extends CamelTestSupport {
    private static final String ECHO_RESPONSE = "<ns1:echoResponse xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
        + "<return xmlns=\"http://cxf.component.camel.apache.org/\">echo Hello World!</return>"
        + "</ns1:echoResponse>";


    protected final String simpleEndpointAddress = "http://localhost:"
            + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/test";
    protected final String simpleEndpointURI = "cxf://" + simpleEndpointAddress
            + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    
    private String testMessage;
    private String receivedMessageCxfPayloadApplyingXPath;
    private String receivedMessageByDom;
    private String receivedMessageStringApplyingXPath;
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(simpleEndpointURI + "&dataFormat=PAYLOAD").to("log:info").process(new Processor() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public void process(final Exchange exchange) throws Exception {
                        Object request = exchange.getIn().getBody();
                        assertIsInstanceOf(CxfPayload.class, request);

                        //attempt 1) applying XPath to exchange.getIn().getBody()
                        receivedMessageCxfPayloadApplyingXPath = XPathBuilder.xpath("//*[name()='arg0']/text()").evaluate(context, request, String.class);
                        
                        //attempt 2) in stead of XPATH, browse the DOM-tree
                        CxfPayload<SoapHeader> payload = (CxfPayload<SoapHeader>) request;
                        Element el = payload.getBody().get(0);
                        Element el2 = (Element) el.getFirstChild();
                        Text textnode = (Text) el2.getFirstChild();
                        receivedMessageByDom = textnode.getNodeValue();
                        
                        textnode = (Text) textnode.getNextSibling();
                        while (textnode != null) {
                        //the textnode appears to have siblings!
                            receivedMessageByDom = receivedMessageByDom + textnode.getNodeValue();
                            textnode = (Text) textnode.getNextSibling();
                        }

                        //attempt 3) apply XPATH after converting CxfPayload to String
                        request = exchange.getIn().getBody(String.class);
                        assertIsInstanceOf(String.class, request);
                        receivedMessageStringApplyingXPath = XPathBuilder.xpath("//*[name()='arg0']/text()").evaluate(context, request, String.class);

                        //build some dummy response 
                        XmlConverter converter = new XmlConverter();
                        Document outDocument = converter.toDOMDocument(ECHO_RESPONSE, exchange);
                        List<Source> outElements = new ArrayList<>();
                        outElements.add(new DOMSource(outDocument.getDocumentElement()));
                        // set the payload header with null
                        CxfPayload<SoapHeader> responsePayload = new CxfPayload<>(null, outElements, null);
                        exchange.getOut().setBody(responsePayload);
                    }
                });
            }
        };
    }

    private void buildTestMessage(int size) {
        testMessage = StringUtils.repeat("x", size);
    }

    @Test
    public void testMessageWithIncreasingSize() throws Exception {

        execTest(1);
        execTest(10);
        execTest(100);
        execTest(1000);
        execTest(10000);
        execTest(100000);

    }

    private void execTest(int size) throws Exception {
        buildTestMessage(size);

        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(simpleEndpointAddress);
        clientBean.setServiceClass(HelloService.class);
        clientBean.setBus(BusFactory.getDefaultBus());

        HelloService client = (HelloService) proxyFactory.create();

        String result = client.echo(testMessage);
        assertEquals("We should get the echo string result from router", "echo Hello World!", result);

        //check received requests
        assertEquals("Lengths of testMessage and receiveMessage should be equal (conversion body to String),", testMessage.length(), receivedMessageStringApplyingXPath.length());
        assertEquals("Lengths of receivedMessageByDom and receivedMessageCxfPayloadApplyingXPath should be equal", receivedMessageCxfPayloadApplyingXPath.length(), receivedMessageByDom.length());
        assertEquals("Lengths of testMessage and receiveMessage should be equal (body is CxfPayload),", testMessage.length(), receivedMessageCxfPayloadApplyingXPath.length());
    }
}
