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

import java.net.SocketTimeoutException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Ignore("Run manually, makes connection to external webservice")
@ContextConfiguration
public class ProducerRemoteRouteTimeOutTest extends AbstractJUnit4SpringContextTests {

    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    @Produce
    private ProducerTemplate template;

    @Test
    public void callStockQuoteWebserviceCosmmonsHttpWith3MillSecondsTimeout() throws Exception {
        try {
            template.requestBody("direct:stockQuoteWebserviceCommonsHttpWith3MillSecondsTimeout", xmlRequestForGoogleStockQuote);
            fail("Miss the expected exception in chain");
        } catch (CamelExecutionException cee) {
            assertTrue(hasThrowableInChain(cee, SocketTimeoutException.class));
        }
    }
    
    @Test
    public void callStockQuoteWebserviceCommonsHttpWith5000MillSecondsTimeout() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceCommonsHttpWith5000MillSecondsTimeout", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));
    }
    
    @Test
    public void callStockQuoteWebserviceJDKWith3MillSecondsTimeout() throws Exception {
        try {
            template.requestBody("direct:stockQuoteWebserviceJDKWith3MillSecondsTimeout", xmlRequestForGoogleStockQuote);
            fail("Miss the expected exception in chain");
        } catch (CamelExecutionException cee) {
            assertTrue(hasThrowableInChain(cee, SocketTimeoutException.class));
        }
    }

    @Test
    public void callStockQuoteWebserviceJDKWith5000MillSecondsTimeout() throws Exception {
        Object result = template.requestBody("direct:stockQuoteWebserviceJDKWith5000MillSecondsTimeout", xmlRequestForGoogleStockQuote);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultMessage = (String) result;
        assertTrue(resultMessage.contains("Google Inc."));
    }

    private static boolean hasThrowableInChain(Throwable throwable, Class<? extends Throwable> clazz) {
        if (throwable == null) {
            return false;
        } else if (clazz.isAssignableFrom(throwable.getClass())) {
            return true;
        }

        return hasThrowableInChain(throwable.getCause(), clazz);
    }
}
