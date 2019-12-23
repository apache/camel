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
package org.apache.camel.component.spring.ws.addressing;

import java.io.StringReader;
import java.net.URI;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.spring.ws.utils.TestUtil;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.addressing.version.Addressing10;
import org.springframework.ws.soap.client.core.SoapActionCallback;

public class ConsumerWSAEndpointMappingRouteTest extends CamelSpringTestSupport {

    private final String xmlBody = "<GetQuote xmlns=\"http://www.stockquotes.edu/\"><symbol>GOOG</symbol></GetQuote>";

    @EndpointInject("mock:testAction")
    private MockEndpoint resultEndpointAction;

    @EndpointInject("mock:testTo")
    private MockEndpoint resultEndpointTo;

    @EndpointInject("mock:testActionAndTo")
    private MockEndpoint resultEndpointActionAndTo;

    @EndpointInject("mock:testToAndAction")
    private MockEndpoint resultEndpointToAndAction;

    @EndpointInject("mock:testToMoreSpecific")
    private MockEndpoint resultEndpointToMoreSpecific;

    @EndpointInject("mock:testActionMoreSpecific")
    private MockEndpoint resultEndpointActionMoreSpecific;

    @EndpointInject("mock:testOutputAndFault")
    private MockEndpoint resultOutputAndFault;

    @EndpointInject("mock:testOutputAndFault2")
    private MockEndpoint resultOutputAndFault2;

    @EndpointInject("mock:testSoapAction")
    private MockEndpoint resultSoapAction;

    private WebServiceTemplate webServiceTemplate;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        webServiceTemplate = applicationContext.getBean("webServiceTemplate", WebServiceTemplate.class);
    }

    @Test
    public void testWSAddressingAction() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback("http://www.stockquotes.edu/myUniqueAction"), TestUtil.NOOP_SOURCE_EXTRACTOR);
        // here is localhost as to by default
        resultEndpointAction.expectedMinimumMessageCount(1);
        resultEndpointAction.assertIsSatisfied();
    }

    @Test
    public void testWSAddressingTo() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback(new URI("http://www.stockquotes.edu/does-not-matter"), new Addressing10(),
                                                                           new URI("http://myUniqueToUrl")), TestUtil.NOOP_SOURCE_EXTRACTOR);

        resultEndpointTo.expectedMinimumMessageCount(1);
        resultEndpointTo.assertIsSatisfied();
    }

    @Test
    public void testWSAddressingActionAndTo() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback(new URI("http://actionAndTo"), new Addressing10(), new URI("http://url4.to")),
                                                TestUtil.NOOP_SOURCE_EXTRACTOR);
        resultEndpointActionAndTo.expectedMinimumMessageCount(1);
        resultEndpointActionAndTo.assertIsSatisfied();
    }

    @Test
    public void testWSAddressingToAndAction() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback(new URI("http://toAndAction"), new Addressing10(), new URI("http://url3.to")),
                                                TestUtil.NOOP_SOURCE_EXTRACTOR);

        resultEndpointToAndAction.expectedMinimumMessageCount(1);
        resultEndpointToAndAction.assertIsSatisfied();
    }

    @Test
    public void testWSAddressingResolveToMoreSpecif() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback(new URI("http://action1"), new Addressing10(), new URI("http://url1.to")),
                                                TestUtil.NOOP_SOURCE_EXTRACTOR);

        resultEndpointToMoreSpecific.expectedMinimumMessageCount(1);
        resultEndpointToMoreSpecific.assertIsSatisfied();

        resultEndpointActionMoreSpecific.expectedMinimumMessageCount(0);
        resultEndpointActionMoreSpecific.assertIsSatisfied();
    }

    @Test
    public void testWSAddressingResolveActionMoreSpecif() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback(new URI("http://action2"), new Addressing10(), new URI("http://url2.to")),
                                                TestUtil.NOOP_SOURCE_EXTRACTOR);

        resultEndpointToMoreSpecific.expectedMinimumMessageCount(0);
        resultEndpointToMoreSpecific.assertIsSatisfied();

        resultEndpointActionMoreSpecific.expectedMinimumMessageCount(1);
        resultEndpointActionMoreSpecific.assertIsSatisfied();
    }

    @Test
    public void testWSAddressingActionResponseActions() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback("http://www.stockquotes.edu/WSAddresingActionReply"), TestUtil.NOOP_SOURCE_EXTRACTOR);
        resultOutputAndFault.expectedMinimumMessageCount(1);
        resultOutputAndFault.assertIsSatisfied();
    }

    @Test
    public void testWSAddressingToResponseActions() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback(new URI("http://doesn-not-matter.com"), new Addressing10(), new URI("http://urlOutputAndFault2.to")),
                                                TestUtil.NOOP_SOURCE_EXTRACTOR);
        resultOutputAndFault2.expectedMinimumMessageCount(1);
        resultOutputAndFault2.assertIsSatisfied();
    }

    @Test(expected = WebServiceIOException.class)
    public void testWrongWSAddressingAction() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new ActionCallback("http://this-is-a-wrong-ws-addressing-action"), TestUtil.NOOP_SOURCE_EXTRACTOR);
        resultEndpointAction.assertIsSatisfied();
    }

    @Test
    public void testClassicalSoapHttpHeaderInterference() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlBody));
        webServiceTemplate.sendSourceAndReceive(source, new SoapActionCallback("http://www.stockquotes.edu/soapHttpHeaderAction"), TestUtil.NOOP_SOURCE_EXTRACTOR);

        resultSoapAction.expectedMinimumMessageCount(1);
        resultSoapAction.assertIsSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"org/apache/camel/component/spring/ws/addresing/ConsumerWSAEndpointMappingRouteTest-context.xml"});
    }
}
