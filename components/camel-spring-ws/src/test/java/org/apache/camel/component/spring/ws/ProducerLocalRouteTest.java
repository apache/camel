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

import javax.xml.transform.Source;

import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.xml.StringSource;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@ContextConfiguration
public class ProducerLocalRouteTest extends AbstractJUnit4SpringContextTests {

    private final String stockQuoteWebserviceUri = "http://localhost/";
    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    @Produce
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint resultEndpoint;

    @EndpointInject("mock:inOnly")
    private MockEndpoint inOnlyEndpoint;

    @Test
    public void consumeStockQuoteWebserviceWithDefaultTemplate() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceWithDefaultTemplate", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test
    public void consumeStockQuoteWebserviceAndPreserveHeaders() throws Exception {
        resultEndpoint.expectedHeaderReceived("helloHeader", "hello world!");

        Object result = template.requestBodyAndHeader("direct:stockQuoteWebserviceMock", xmlRequestForGoogleStockQuote, "helloHeader", "hello world!");

        assertNotNull(result);
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void consumeStockQuoteWebservice() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebservice", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test
    public void consumeStockQuoteWebserviceWithCamelStringSourceInput() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebservice", new StringSource(xmlRequestForGoogleStockQuote));

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test
    public void consumeStockQuoteWebserviceWithNonDefaultMessageFactory() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceWithNonDefaultMessageFactory", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test
    public void consumeStockQuoteWebserviceAndConvertResult() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceAsString", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));
    }

    @Test
    public void consumeStockQuoteWebserviceAndProvideEndpointUriByHeader() throws Exception {
        Object result = template.requestBodyAndHeader("direct:stockQuoteWebserviceWithoutDefaultUri", xmlRequestForGoogleStockQuote,
                SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI, stockQuoteWebserviceUri);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));
    }

    @Test
    public void consumeStockQuoteWebserviceInOnly() throws Exception {
        inOnlyEndpoint.expectedExchangePattern(ExchangePattern.InOnly);
        inOnlyEndpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:stockQuoteWebserviceInOnly", xmlRequestForGoogleStockQuote, "foo", "bar");

        inOnlyEndpoint.assertIsSatisfied();

        Message in = inOnlyEndpoint.getReceivedExchanges().get(0).getIn();

        Object result = in.getBody();
        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));

        Object bar = in.getHeader("foo");
        assertEquals("The header value should have been preserved", "bar", bar);
    }
}
