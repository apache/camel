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

import java.io.StringReader;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ConsumerSoapHeaderFilterTest extends CamelSpringTestSupport {

    private static final SourceExtractor<Object> NOOP_SOURCE_EXTRACTOR = source -> null;

    private final String xmlRequest = "<GetQuote xmlns=\"http://www.stockquotes.edu/\"><symbol>GOOG</symbol></GetQuote>";

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private WebServiceTemplate webServiceTemplate;

    @Override
    public void doPostSetup() {
        webServiceTemplate = applicationContext.getBean("webServiceTemplate", WebServiceTemplate.class);
    }

    @Test
    public void internalCamelHeaderFromInboundSoapHeaderIsFiltered() throws Exception {
        result.expectedMessageCount(1);

        Source source = new StreamSource(new StringReader(xmlRequest));
        webServiceTemplate.sendSourceAndReceive(source, message -> {
            SoapMessage soap = (SoapMessage) message;
            soap.setSoapAction("http://www.stockquotes.edu/GetQuote");
            SoapHeader soapHeader = soap.getSoapHeader();
            // a regular application-level header element
            soapHeader.addHeaderElement(new QName("http://example.com/test", "MessageID")).setText("1234567890");
            // an attacker-supplied element whose local name lives in the internal Camel header namespace
            soapHeader.addHeaderElement(new QName("http://example.com/test", "CamelFileName")).setText("../../etc/passwd");
        }, NOOP_SOURCE_EXTRACTOR);

        result.assertIsSatisfied();

        Exchange consumed = result.getExchanges().get(0);
        // a regular application-level SOAP header element is still mapped to an Exchange header
        assertNotNull(consumed.getIn().getHeader("MessageID"),
                "Application-level SOAP header element should be propagated");
        // an attacker-supplied internal Camel* header name must be filtered out by the HeaderFilterStrategy
        assertNull(consumed.getIn().getHeader("CamelFileName"),
                "Internal Camel* header coming from an inbound SOAP header must be filtered");
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/spring/ws/ConsumerSoapHeaderFilterTest-context.xml");
    }
}
