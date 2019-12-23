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
package org.apache.camel.builder;

import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.KeyManagementException;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test to test exception configuration
 */
public class ExceptionBuilderTest extends ContextTestSupport {

    private static final String MESSAGE_INFO = "messageInfo";
    private static final String RESULT_QUEUE = "mock:result";
    private static final String ERROR_QUEUE = "mock:error";
    private static final String BUSINESS_ERROR_QUEUE = "mock:badBusiness";
    private static final String SECURITY_ERROR_QUEUE = "mock:securityError";

    @Test
    public void testNPE() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm a NPE");

        try {
            template.sendBody("direct:a", "Hello NPE");
            fail("Should have thrown a NullPointerException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof NullPointerException);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);
    }

    @Test
    public void testIOException() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm somekind of IO exception");

        try {
            template.sendBody("direct:a", "Hello IO");
            fail("Should have thrown a IOException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IOException);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);
    }

    @Test
    public void testException() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm just exception");

        try {
            template.sendBody("direct:a", "Hello Exception");
            fail("Should have thrown a Exception");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof Exception);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);
    }

    @Test
    public void testMyBusinessException() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(BUSINESS_ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm my business is not going to well");

        try {
            template.sendBody("direct:a", "Hello business");
            fail("Should have thrown a MyBusinessException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof MyBusinessException);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);
    }

    @Test
    public void testSecurityConfiguredWithTwoExceptions() throws Exception {
        // test that we also handles a configuration with 2 or more exceptions
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(SECURITY_ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm some security error");

        try {
            template.sendBody("direct:a", "I am not allowed to do this");
            fail("Should have thrown a GeneralSecurityException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof GeneralSecurityException);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);
    }

    @Test
    public void testSecurityConfiguredWithExceptionList() throws Exception {
        // test that we also handles a configuration with a list of exceptions
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm some access error");

        try {
            template.sendBody("direct:a", "I am not allowed to access this");
            fail("Should have thrown a GeneralSecurityException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IllegalAccessException);
            // expected
        }

        MockEndpoint.assertIsSatisfied(result, mock);
    }

    public static class MyBaseBusinessException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public static class MyBusinessException extends MyBaseBusinessException {
        private static final long serialVersionUID = 1L;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                // START SNIPPET: exceptionBuilder1
                onException(NullPointerException.class).maximumRedeliveries(0).setHeader(MESSAGE_INFO, constant("Damm a NPE")).to(ERROR_QUEUE);

                onException(IOException.class).redeliveryDelay(10).maximumRedeliveries(3).maximumRedeliveryDelay(30 * 1000L).backOffMultiplier(1.0).useExponentialBackOff()
                    .setHeader(MESSAGE_INFO, constant("Damm somekind of IO exception")).to(ERROR_QUEUE);

                onException(Exception.class).redeliveryDelay(0).maximumRedeliveries(2).setHeader(MESSAGE_INFO, constant("Damm just exception")).to(ERROR_QUEUE);

                onException(MyBaseBusinessException.class).redeliveryDelay(0).maximumRedeliveries(3).setHeader(MESSAGE_INFO, constant("Damm my business is not going to well"))
                    .to(BUSINESS_ERROR_QUEUE);

                onException(GeneralSecurityException.class, KeyException.class).maximumRedeliveries(1).setHeader(MESSAGE_INFO, constant("Damm some security error"))
                    .to(SECURITY_ERROR_QUEUE);

                onException(InstantiationException.class, IllegalAccessException.class, ClassNotFoundException.class).maximumRedeliveries(0)
                    .setHeader(MESSAGE_INFO, constant("Damm some access error")).to(ERROR_QUEUE);
                // END SNIPPET: exceptionBuilder1

                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello NPE".equals(s)) {
                            throw new NullPointerException();
                        } else if ("Hello IO".equals(s)) {
                            throw new ConnectException("Forced for testing - cannot connect to remote server");
                        } else if ("Hello Exception".equals(s)) {
                            throw new CamelExchangeException("Forced for testing", exchange);
                        } else if ("Hello business".equals(s)) {
                            throw new MyBusinessException();
                        } else if ("I am not allowed to do this".equals(s)) {
                            throw new KeyManagementException();
                        } else if ("I am not allowed to access this".equals(s)) {
                            throw new IllegalAccessException();
                        }
                        exchange.getMessage().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }
}
