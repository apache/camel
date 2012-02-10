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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.spring.ws.SpringWebserviceProducer.CamelHttpUrlConnectionMessageSender;
import org.apache.camel.component.spring.ws.SpringWebserviceProducer.CamelHttpsUrlConnectionMessageSender;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import org.springframework.ws.transport.http.HttpsUrlConnectionMessageSender;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@ContextConfiguration
public class ProducerRemoteRouteTimeOutTest extends AbstractJUnit4SpringContextTests {

    private final String xmlRequestForGoogleStockQuote = "<GetQuote xmlns=\"http://www.webserviceX.NET/\"><symbol>GOOG</symbol></GetQuote>";

    @Produce
    private ProducerTemplate template;

    @Ignore("Run manually, makes connection to external webservice")
    @Test
    public void callStockQuoteWebserviceCommonsHttpWith3MillSecondsTimeout() throws Exception {
        try {
            template.requestBody("direct:stockQuoteWebserviceCommonsHttpWith3MillSecondsTimeout", xmlRequestForGoogleStockQuote);
            fail("Miss the expected exception in chain");
        } catch (CamelExecutionException cee) {
            assertTrue(hasThrowableInChain(cee, SocketTimeoutException.class));
        }
    }

    @Ignore("Run manually, makes connection to external webservice")
    @Test
    public void callStockQuoteWebserviceCommonsHttpWith5000MillSecondsTimeout() throws Exception {
        template.requestBody("direct:stockQuoteWebserviceCommonsHttpWith5000MillSecondsTimeout", xmlRequestForGoogleStockQuote);
    }

    @Ignore("Run manually, makes connection to external webservice")
    @Test
    public void callStockQuoteWebserviceJDKWith3MillSecondsTimeout() throws Exception {
        try {
            template.requestBody("direct:stockQuoteWebserviceJDKWith3MillSecondsTimeout", xmlRequestForGoogleStockQuote);
            fail("Miss the expected exception in chain");
        } catch (CamelExecutionException cee) {
            assertTrue(hasThrowableInChain(cee, SocketTimeoutException.class));
        }
    }

    @Ignore("Run manually, makes connection to external webservice")
    @Test
    public void callStockQuoteWebserviceJDKWith5000MillSecondsTimeout() throws Exception {
        template.requestBody("direct:stockQuoteWebserviceJDKWith5000MillSecondsTimeout", xmlRequestForGoogleStockQuote);
    }

    private static boolean hasThrowableInChain(Throwable throwable, Class<? extends Throwable> clazz) {
        if (throwable == null) {
            return false;
        } else if (clazz.isAssignableFrom(throwable.getClass())) {
            return true;
        }

        return hasThrowableInChain(throwable.getCause(), clazz);
    }

    @Test
    public void verifyTheFieldPopulationFromHttpUrlConnectionMessageSenderToCamelHttpUrlConnectionMessageSender() throws Exception {
        HttpUrlConnectionMessageSender fromConnectionMessageSender = new HttpUrlConnectionMessageSender();
        fromConnectionMessageSender.setAcceptGzipEncoding(false);

        CamelHttpUrlConnectionMessageSender toConnectionMessageSender = new CamelHttpUrlConnectionMessageSender(new SpringWebserviceConfiguration(), fromConnectionMessageSender);
        assertFalse("acceptGzipEncoding property didn't get populated", toConnectionMessageSender.isAcceptGzipEncoding());

        fromConnectionMessageSender.setAcceptGzipEncoding(true);
        toConnectionMessageSender = new CamelHttpUrlConnectionMessageSender(new SpringWebserviceConfiguration(), fromConnectionMessageSender);
        assertTrue("acceptGzipEncoding property didn't get populated properly!", toConnectionMessageSender.isAcceptGzipEncoding());
    }

    @Test
    public void verifyTheFieldPopulationFromHttpsUrlConnectionMessageSenderToCamelHttpsUrlConnectionMessageSender() throws Exception {
        HttpsUrlConnectionMessageSender fromConnectionMessageSender = new HttpsUrlConnectionMessageSender();
        fromConnectionMessageSender.setAcceptGzipEncoding(false);
        fromConnectionMessageSender.setHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String s, SSLSession sslsession) {
                return false;
            }
        });
        fromConnectionMessageSender.setKeyManagers(new KeyManager[] {new KeyManager() {
        }});
        fromConnectionMessageSender.setSecureRandom(new SecureRandom());
        fromConnectionMessageSender.setSslProtocol("sslProtocol");
        fromConnectionMessageSender.setSslProvider("sslProvider");
        fromConnectionMessageSender.setTrustManagers(new TrustManager[] {new TrustManager() {
        }});

        CamelHttpsUrlConnectionMessageSender toConnectionMessageSender = new CamelHttpsUrlConnectionMessageSender(new SpringWebserviceConfiguration(), fromConnectionMessageSender);

        assertFalse("acceptGzipEncoding field didn't get populated", toConnectionMessageSender.isAcceptGzipEncoding());
        for (Field expectedField : fromConnectionMessageSender.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(expectedField.getModifiers())) {
                continue;
            }
            expectedField.setAccessible(true);
            String fieldName = expectedField.getName();

            Field actualField = toConnectionMessageSender.getClass().getSuperclass().getDeclaredField(fieldName);
            actualField.setAccessible(true);

            assertSame("The field '" + fieldName + "' didn't get populated properly!", expectedField.get(fromConnectionMessageSender), actualField.get(toConnectionMessageSender));
        }
    }

}
