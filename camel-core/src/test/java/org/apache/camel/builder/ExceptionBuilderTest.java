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
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to test exception configuration
 */
public class ExceptionBuilderTest extends ContextTestSupport {

    private static final String MESSAGE_INFO = "messageInfo";
    private static final String ERROR_QUEUE = "mock:error";
    private static final String BUSINESS_ERROR_QUEUE = "mock:badBusiness";
    private static final String SECURITY_ERROR_QUEUE = "mock:securityError";

    public void testNPE() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm a NPE");

        template.sendBody("direct:a", "Hello NPE");

        mock.assertIsSatisfied();
    }

    public void testIOException() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm somekind of IO exception");

        template.sendBody("direct:a", "Hello IO");

        mock.assertIsSatisfied();
    }

    public void testException() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm just exception");

        template.sendBody("direct:a", "Hello Exception");

        mock.assertIsSatisfied();
    }

    public void testMyBusinessException() throws Exception {
        MockEndpoint mock = getMockEndpoint(BUSINESS_ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm my business is not going to well");

        template.sendBody("direct:a", "Hello business");

        mock.assertIsSatisfied();
    }

    public void testSecurityConfiguredWithTwoExceptions() throws Exception {
        // test that we also handles a configuration with 2 or more exceptions
        MockEndpoint mock = getMockEndpoint(SECURITY_ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm some security error");

        template.sendBody("direct:a", "I am not allowed to do this");

        mock.assertIsSatisfied();
    }

    public static class MyBaseBusinessException extends Exception {
    }

    public static class MyBusinessException extends MyBaseBusinessException {
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: exceptionBuilder1
                exception(NullPointerException.class)
                    .maximumRedeliveries(1)
                    .setHeader(MESSAGE_INFO, "Damm a NPE")
                    .to(ERROR_QUEUE);

                exception(IOException.class)
                    .initialRedeliveryDelay(5000L)
                    .maximumRedeliveries(3)
                    .backOffMultiplier(1.0)
                    .useExponentialBackOff()
                    .setHeader(MESSAGE_INFO, "Damm somekind of IO exception")
                    .to(ERROR_QUEUE);

                exception(Exception.class)
                    .initialRedeliveryDelay(1000L)
                    .maximumRedeliveries(2)
                    .setHeader(MESSAGE_INFO, "Damm just exception")
                    .to(ERROR_QUEUE);
                // END SNIPPET: exceptionBuilder1

                exception(MyBaseBusinessException.class)
                    .initialRedeliveryDelay(1000L)
                    .maximumRedeliveries(3)
                    .setHeader(MESSAGE_INFO, "Damm my business is not going to well")
                    .to(BUSINESS_ERROR_QUEUE);

                exception(GeneralSecurityException.class).exception(KeyException.class)
                    .maximumRedeliveries(1)
                    .setHeader(MESSAGE_INFO, "Damm some security error")
                    .to(SECURITY_ERROR_QUEUE);


                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello NPE".equals(s)) {
                            throw new NullPointerException();
                        } else if ("Hello IO".equals(s)) {
                            throw new ConnectException("Forced for testing - can not connect to remote server");
                        } else if ("Hello Exception".equals(s)) {
                            throw new CamelExchangeException("Forced for testing", exchange);
                        } else if ("Hello business".equals(s)) {
                            throw new MyBusinessException();
                        } else if ("I am not allowed to do this".equals(s)) {
                            throw new KeyManagementException();
                        }
                        exchange.getOut().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }

}

