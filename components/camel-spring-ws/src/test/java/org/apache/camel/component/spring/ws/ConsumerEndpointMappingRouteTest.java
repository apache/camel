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
package org.apache.camel.component.spring.ws;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;

public class ConsumerEndpointMappingRouteTest extends CamelSpringTestSupport {

    private static final SourceExtractor<Object> NOOP_SOURCE_EXTRACTOR = new SourceExtractor<Object>() {
        public Object extractData(Source source) throws IOException, TransformerException {
            return null;
        }
    };

    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.stockquotes.edu/\"><symbol>GOOG</symbol></GetQuote>";
    private final String xmlRequestForGoogleStockQuoteNoNamespace = "<GetQuote><symbol>GOOG</symbol></GetQuote>";
    private final String xmlRequestForGoogleStockQuoteNoNamespaceDifferentBody = "<GetQuote><symbol>GRABME</symbol></GetQuote>";

    @EndpointInject("mock:testRootQName")
    private MockEndpoint resultEndpointRootQName;

    @EndpointInject("mock:testSoapAction")
    private MockEndpoint resultEndpointSoapAction;

    @EndpointInject("mock:testUri")
    private MockEndpoint resultEndpointUri;

    @EndpointInject("mock:testUriPath")
    private MockEndpoint resultEndpointUriPath;

    @EndpointInject("mock:testXPath")
    private MockEndpoint resultEndpointXPath;

    private WebServiceTemplate webServiceTemplate;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        webServiceTemplate = applicationContext.getBean("webServiceTemplate", WebServiceTemplate.class);
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
        webServiceTemplate.sendSourceAndReceive(getDefaultXmlRequestSource(), new SoapActionCallback("http://www.stockquotes.edu/GetQuote"), NOOP_SOURCE_EXTRACTOR);
        resultEndpointSoapAction.expectedMinimumMessageCount(1);
        resultEndpointSoapAction.assertIsSatisfied();
    }

    @Test(expected = WebServiceIOException.class)
    public void testWrongSoapAction() throws Exception {
        webServiceTemplate.sendSourceAndReceive(getDefaultXmlRequestSource(), new SoapActionCallback("http://this-is-a-wrong-soap-action"), NOOP_SOURCE_EXTRACTOR);
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
        webServiceTemplate.sendSourceAndReceive("http://localhost/stockquote2", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        resultEndpointUri.expectedMinimumMessageCount(1);
        resultEndpointUri.assertIsSatisfied();
    }

    @Test
    public void testUriPath() throws Exception {
        webServiceTemplate.sendSourceAndReceive("http://localhost/stockquote3/service", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        webServiceTemplate.sendSourceAndReceive("http://localhost:8080/stockquote3/service", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        resultEndpointUriPath.expectedMessageCount(2);
        resultEndpointUriPath.assertIsSatisfied();
    }

    @Test
    public void testUriPathWildcard() throws Exception {
        webServiceTemplate.sendSourceAndReceive("http://localhost/stockquote4/service", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        webServiceTemplate.sendSourceAndReceive("http://localhost:8080/stockquote4/service", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        webServiceTemplate.sendSourceAndReceive("http://localhost/stockquote4/service/", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        webServiceTemplate.sendSourceAndReceive("http://localhost:8080/stockquote4/service/", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        webServiceTemplate.sendSourceAndReceive("http://localhost/stockquote4/service/test", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        webServiceTemplate.sendSourceAndReceive("http://0.0.0.0:11234/stockquote4/service/test", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        resultEndpointUriPath.expectedMessageCount(6);
        resultEndpointUriPath.assertIsSatisfied();
    }

    @Test(expected = WebServiceIOException.class)
    public void testWrongUri() throws Exception {
        webServiceTemplate.sendSourceAndReceive("http://localhost/wrong", getDefaultXmlRequestSource(), NOOP_SOURCE_EXTRACTOR);
        resultEndpointUri.assertIsNotSatisfied();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                new String[]{"org/apache/camel/component/spring/ws/ConsumerEndpointMappingRouteTest-context.xml"});
    }

    private Source getDefaultXmlRequestSource() {
        return new StreamSource(new StringReader(xmlRequestForGoogleStockQuoteNoNamespace));
    }
}
