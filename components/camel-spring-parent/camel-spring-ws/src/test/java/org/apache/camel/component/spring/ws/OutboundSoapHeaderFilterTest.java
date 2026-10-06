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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.xml.namespace.QName;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundSoapHeaderFilterTest extends CamelSpringTestSupport {

    private final String xmlRequest = "<GetQuote xmlns=\"http://www.stockquotes.edu/\"><symbol>GOOG</symbol></GetQuote>";

    @EndpointInject("mock:received")
    private MockEndpoint received;

    @Test
    void internalCamelHeadersAreNotWrittenIntoTheConsumerResponse() {
        List<String> attributes = responseSoapHeaderAttributes("http://www.stockquotes.edu/Respond");

        assertTrue(attributes.contains("TransactionId"),
                "Application-level message header should be written into the SOAP response header");
        assertFalse(attributes.stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).startsWith("camel")),
                "Internal Camel* headers must not be written into the SOAP response header: " + attributes);
    }

    @Test
    void internalCamelHeadersAreNotWrittenIntoTheProducerRequest() throws Exception {
        received.expectedMessageCount(1);

        template.send("direct:send", exchange -> {
            exchange.getIn().setBody(xmlRequest);
            exchange.getIn().setHeader("CamelHttpUri", "http://localhost/backend");
            exchange.getIn().setHeader("cAmElFileName", "request.xml");
            exchange.getIn().setHeader("TransactionId", "42");
        });

        received.assertIsSatisfied();
        Exchange exchange = received.getExchanges().get(0);
        String soapHeader = exchange.getIn().getHeader(SpringWebserviceConstants.SPRING_WS_SOAP_HEADER, String.class);
        assertTrue(soapHeader.contains("TransactionId"),
                "Application-level message header should be written into the SOAP request header");
        assertFalse(soapHeader.toLowerCase(Locale.ROOT).contains("camelhttpuri"),
                "Internal Camel* headers must not be written into the SOAP request header: " + soapHeader);
        assertFalse(soapHeader.toLowerCase(Locale.ROOT).contains("camelfilename"),
                "Internal Camel* headers must not be written into the SOAP request header: " + soapHeader);
    }

    @Test
    void endpointHeaderFilterStrategyIsApplied() {
        List<String> attributes = responseSoapHeaderAttributes("http://www.stockquotes.edu/RespondPassThrough");

        // the endpoint's own strategy decides, so a strategy that filters nothing writes every header
        assertTrue(attributes.contains("CamelHttpUri"),
                "The headerFilterStrategy configured on the endpoint should be applied: " + attributes);
    }

    private List<String> responseSoapHeaderAttributes(String soapAction) {
        WebServiceTemplate client = applicationContext.getBean("webServiceTemplate", WebServiceTemplate.class);
        return client.sendAndReceive(
                request -> {
                    try {
                        TransformerFactory.newInstance().newTransformer()
                                .transform(new StreamSource(new StringReader(xmlRequest)), request.getPayloadResult());
                    } catch (TransformerException e) {
                        throw new IllegalStateException(e);
                    }
                    ((SoapMessage) request).setSoapAction(soapAction);
                },
                response -> {
                    List<String> names = new ArrayList<>();
                    SoapHeader soapHeader = ((SoapMessage) response).getSoapHeader();
                    Iterator<QName> attributes = soapHeader.getAllAttributes();
                    while (attributes.hasNext()) {
                        names.add(attributes.next().getLocalPart());
                    }
                    return names;
                });
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/spring/ws/OutboundSoapHeaderFilterTest-context.xml");
    }
}
