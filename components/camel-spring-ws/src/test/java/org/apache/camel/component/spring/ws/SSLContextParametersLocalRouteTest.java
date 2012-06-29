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

import javax.annotation.Resource;
import javax.xml.transform.Source;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.StringSource;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// TODO: enable after upgrading to Spring-WS 2.0.5
@Ignore("Under JDK 7 requires minimum Spring-WS 2.0.5 to pass. Should be enabled again as soon as the upgrade to Spring-WS 2.0.5 has been done!")
@ContextConfiguration
public class SSLContextParametersLocalRouteTest extends AbstractJUnit4SpringContextTests {

    private final String stockQuoteWebserviceUri = "https://localhost";
    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    @Produce
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;
    
    @Resource
    private int port;

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
                SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI, stockQuoteWebserviceUri + ":" + port);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));
    }
}
