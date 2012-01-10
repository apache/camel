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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to test exception configuration
 */
public class ExceptionBuilderWithHandledExceptionTest extends ContextTestSupport {

    private static final String MESSAGE_INFO = "messageInfo";
    private static final String RESULT_QUEUE = "mock:result";
    private static final String ERROR_QUEUE = "mock:error";

    public void testHandledException() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Handled exchange with NullPointerException");

        template.sendBody("direct:a", "Hello NPE");
        MockEndpoint.assertIsSatisfied(result, mock);
    }

    public void testHandledExceptionWithExpression() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Handled exchange with IOException");

        template.sendBodyAndHeader("direct:a", "Hello IOE", "foo", "bar");
        MockEndpoint.assertIsSatisfied(result, mock);
    }

    public void testUnhandledException() throws Exception {
        MockEndpoint result = getMockEndpoint(RESULT_QUEUE);
        result.expectedMessageCount(0);
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Handled exchange with IOException");
        
        try {
            template.sendBodyAndHeader("direct:a", "Hello IOE", "foo", "something that does not match");
            fail("Should have thrown a IOException");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof IOException);
            // expected, failure is not handled because predicate doesn't match
        }

        MockEndpoint.assertIsSatisfied(result, mock);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                // START SNIPPET: exceptionBuilder1
                onException(NullPointerException.class)
                    .maximumRedeliveries(0)
                    .handled(true)
                    .setHeader(MESSAGE_INFO, constant("Handled exchange with NullPointerException"))
                    .to(ERROR_QUEUE);

                onException(IOException.class)
                    .maximumRedeliveries(0)
                    .handled(header("foo").isEqualTo("bar"))
                    .setHeader(MESSAGE_INFO, constant("Handled exchange with IOException"))
                    .to(ERROR_QUEUE);
                // END SNIPPET: exceptionBuilder1

                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello NPE".equals(s)) {
                            throw new NullPointerException();
                        } else if ("Hello IOE".equals(s)) {
                            // specialized IOException
                            throw new ConnectException("Forced for testing - cannot connect to remote server");
                        }
                        exchange.getOut().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }
}
