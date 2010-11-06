/**
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
package org.apache.camel.component.spring.ws;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

public class ConsumerEndpointMappingRouteTest extends CamelSpringTestSupport {

    private static final SourceExtractor NOOP_SOURCE_EXTRACTOR = new SourceExtractor() {
        public Object extractData(Source source) throws IOException, TransformerException {
            return null;
        }
    };

    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.stockquotes.edu/\"><symbol>GOOG</symbol></GetQuote>";
    private final String xmlRequestForGoogleStockQuoteNoNamespace = "<GetQuote><symbol>GOOG</symbol></GetQuote>";
    private final String xmlRequestForGoogleStockQuoteNoNamespaceDifferentBody = "<GetQuote><symbol>GRABME</symbol></GetQuote>";

    @EndpointInject(uri = "mock:testRootQName")
    private MockEndpoint resultEndpointRootQName;

    @EndpointInject(uri = "mock:testSoapAction")
    private MockEndpoint resultEndpointSoapAction;

    @EndpointInject(uri = "mock:testUri")
    private MockEndpoint resultEndpointUri;

    @EndpointInject(uri = "mock:testXPath")
    private MockEndpoint resultEndpointXPath;

    private WebServiceTemplate webServiceTemplate;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        webServiceTemplate = (WebServiceTemplate) applicationContext.getBean("webServiceTemplate");
    }

    @Test
    public void testRootQName() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuote));
        webServiceTemplate.sendSourceAndReceive(source, NOOP_SOURCE_EXTRACTOR);
        resultEndpointRootQName.expectedMinimumMessageCount(1);
        resultEndpointRootQName.assertIsSatisfied();
    }

    @Test
    public void testSoapAction() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespace));
        webServiceTemplate.sendSourceAndReceive(source, new SoapActionCallback("http://www.stockquotes.edu/GetQuote"), NOOP_SOURCE_EXTRACTOR);
        resultEndpointSoapAction.expectedMinimumMessageCount(1);
        resultEndpointSoapAction.assertIsSatisfied();
    }

    @Test(expected = WebServiceIOException.class)
    public void testWrongSoapAction() throws Exception {
        if (isJava15()) {
            // does not work on JDK 1.5 due net.javacrumbs.spring-ws-test is not JDK 1.5 compatible
            throw new WebServiceIOException("Forced by JDK 1.5");
        }
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespace));
        webServiceTemplate.sendSourceAndReceive(source, new SoapActionCallback("http://this-is-a-wrong-soap-action"), NOOP_SOURCE_EXTRACTOR);
        resultEndpointSoapAction.assertIsNotSatisfied();
    }

    @Test
    public void testXPath() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespaceDifferentBody));
        webServiceTemplate.sendSourceAndReceive(source, NOOP_SOURCE_EXTRACTOR);
        resultEndpointXPath.expectedMinimumMessageCount(1);
        resultEndpointXPath.assertIsSatisfied();
    }

    @Test
    public void testUri() throws Exception {
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespace));
        webServiceTemplate.sendSourceAndReceive("http://localhost/stockquote2", source, NOOP_SOURCE_EXTRACTOR);
        resultEndpointUri.expectedMinimumMessageCount(1);
        resultEndpointUri.assertIsSatisfied();
    }

    @Test(expected = WebServiceIOException.class)
    public void testWrongUri() throws Exception {
        if (isJava15()) {
            // does not work on JDK 1.5 due net.javacrumbs.spring-ws-test is not JDK 1.5 compatible
            throw new WebServiceIOException("Forced by JDK 1.5");
        }
        StreamSource source = new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespace));
        webServiceTemplate.sendSourceAndReceive("http://localhost/wrong", source, NOOP_SOURCE_EXTRACTOR);
        resultEndpointUri.assertIsNotSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                new String[]{"org/apache/camel/component/spring/ws/ConsumerEndpointMappingRouteTest-context.xml"});
    }
}
