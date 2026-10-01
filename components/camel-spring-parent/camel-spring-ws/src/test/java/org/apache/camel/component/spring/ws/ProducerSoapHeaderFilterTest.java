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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.soap.SoapHeaderElement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProducerSoapHeaderFilterTest extends CamelSpringTestSupport {

    private final String xmlRequest = "<GetQuote xmlns=\"http://www.stockquotes.edu/\"><symbol>GOOG</symbol></GetQuote>";

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("mock:resultCustomStrategy")
    private MockEndpoint resultCustomStrategy;

    @Test
    void internalCamelAttributeFromResponseSoapHeaderIsFiltered() throws Exception {
        result.expectedMessageCount(1);

        template.requestBody("direct:responseHeaderOverride", xmlRequest);

        result.assertIsSatisfied();
        Message message = result.getExchanges().get(0).getMessage();
        // the raw SOAP header and a regular application-level attribute are still mapped
        assertNotNull(message.getHeader(SpringWebserviceConstants.SPRING_WS_SOAP_HEADER),
                "The SOAP response header should be propagated");
        assertEquals("1234567890", message.getHeader("MessageId"),
                "Application-level SOAP response header attribute should be propagated");
        // attribute names in the internal Camel header namespace are filtered out, whatever their case
        assertNull(message.getHeader(Exchange.HTTP_URI),
                "Internal Camel* header coming from a SOAP response header attribute must be filtered");
        assertNull(message.getHeader(Exchange.HTTP_QUERY),
                "Internal Camel* header coming from a SOAP response header attribute must be filtered case-insensitively");
    }

    @Test
    void internalCamelElementFromResponseSoapHeaderIsFiltered() throws Exception {
        result.expectedMessageCount(1);

        template.requestBody("direct:responseHeaderOverride", xmlRequest);

        result.assertIsSatisfied();
        Message message = result.getExchanges().get(0).getMessage();
        // a regular application-level element is still mapped
        assertInstanceOf(SoapHeaderElement.class, message.getHeader("TransactionId"),
                "Application-level SOAP response header element should be propagated");
        // an element whose local name is in the internal Camel header namespace is filtered out
        assertNull(message.getHeader(Exchange.FILE_NAME),
                "Internal Camel* header coming from a SOAP response header element must be filtered");
    }

    @Test
    void customHeaderFilterStrategyIsApplied() throws Exception {
        resultCustomStrategy.expectedMessageCount(1);

        template.requestBody("direct:responseHeaderOverrideCustomStrategy", xmlRequest);

        resultCustomStrategy.assertIsSatisfied();
        Message message = resultCustomStrategy.getExchanges().get(0).getMessage();
        // the endpoint's own strategy decides, so a strategy that filters nothing maps every name
        assertEquals("http://localhost/other", message.getHeader(Exchange.HTTP_URI),
                "The headerFilterStrategy configured on the endpoint should be applied");
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/spring/ws/ProducerSoapHeaderFilterTest-context.xml");
    }
}
