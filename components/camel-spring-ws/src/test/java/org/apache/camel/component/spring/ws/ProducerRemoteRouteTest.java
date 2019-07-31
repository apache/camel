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

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.xml.StringSource;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Ignore("Run manually, makes connection to external webservice")
@ContextConfiguration
public class ProducerRemoteRouteTest extends AbstractJUnit4SpringContextTests {

    private final String stockQuoteWebserviceUri = "http://www.webservicex.net/stockquote.asmx";
    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    @Produce
    private ProducerTemplate template;

    @Test(timeout = 5000)
    public void consumeStockQuoteWebserviceWithDefaultTemplate() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceWithDefaultTemplate", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test(timeout = 5000)
    public void consumeStockQuoteWebservice() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebservice", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test(timeout = 5000)
    public void consumeStockQuoteWebserviceWithCamelStringSourceInput() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebservice", new StringSource(xmlRequestForGoogleStockQuote));

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test(timeout = 5000)
    public void consumeStockQuoteWebserviceWithNonDefaultMessageFactory() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceWithNonDefaultMessageFactory", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof Source);
    }

    @Test(timeout = 5000)
    public void consumeStockQuoteWebserviceAndConvertResult() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceAsString", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));
    }

    @Test(timeout = 5000)
    public void consumeStockQuoteWebserviceAndProvideEndpointUriByHeader() throws Exception {
        Object result = template.requestBodyAndHeader("direct:stockQuoteWebserviceWithoutDefaultUri", xmlRequestForGoogleStockQuote,
                SpringWebserviceConstants.SPRING_WS_ENDPOINT_URI, stockQuoteWebserviceUri);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));
    }
}
