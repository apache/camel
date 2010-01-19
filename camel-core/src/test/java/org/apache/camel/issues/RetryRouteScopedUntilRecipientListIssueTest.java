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
package org.apache.camel.issues;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
import org.apache.camel.Header;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.impl.JndiRegistry;

/**
 * @version $Revision$
 */
public class RetryRouteScopedUntilRecipientListIssueTest extends ContextTestSupport {

    protected static volatile int invoked;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRetryBean", new MyRetryBean());
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.addEndpoint("fail", new DefaultEndpoint() {
            public Producer createProducer() throws Exception {
                return new DefaultProducer(this) {
                    public void process(Exchange exchange) throws Exception {
                        exchange.setException(new IllegalArgumentException("Damn"));
                    }
                };
            }

            public Consumer createConsumer(Processor processor) throws Exception {
                return null;
            }

            @Override
            protected String createEndpointUri() {
                return "fail";
            }

            public boolean isSingleton() {
                return true;
            }
        });

        context.addEndpoint("not-fail", new DefaultEndpoint() {
            public Producer createProducer() throws Exception {
                return new DefaultProducer(this) {
                    public void process(Exchange exchange) throws Exception {
                        // noop
                    }
                };
            }

            public Consumer createConsumer(Processor processor) throws Exception {
                return null;
            }

            @Override
            protected String createEndpointUri() {
                return "not-fail";
            }

            public boolean isSingleton() {
                return true;
            }
        });

        return context;
    }

    public void testRetryUntilRecipientListOkOnly() throws Exception {
        invoked = 0;

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(1);

        template.sendBodyAndHeader("seda:start", "Hello World", "recipientListHeader", "direct:foo");

        assertMockEndpointsSatisfied();

        context.stop();

        assertEquals(0, invoked);
    }

    public void testRetryUntilRecipientListFailOnly() throws Exception {
        invoked = 0;

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(0);

        template.sendBodyAndHeader("seda:start", "Hello World", "recipientListHeader", "fail");

        assertMockEndpointsSatisfied();

        context.stop();

        assertEquals(3, invoked);
    }

    public void testRetryUntilRecipientListFailAndOk() throws Exception {
        invoked = 0;

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(0);

        template.sendBodyAndHeader("seda:start", "Hello World", "recipientListHeader", "fail,direct:foo");

        assertMockEndpointsSatisfied();

        context.stop();

        assertEquals(3, invoked);
    }

    public void testRetryUntilRecipientListOkAndFail() throws Exception {
        invoked = 0;

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(0);

        template.sendBodyAndHeader("seda:start", "Hello World", "recipientListHeader", "direct:foo,fail");

        assertMockEndpointsSatisfied();

        context.stop();

        assertEquals(3, invoked);
    }

    public void testRetryUntilRecipientNotFail() throws Exception {
        invoked = 0;

        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);

        template.sendBodyAndHeader("seda:start", "Hello World", "recipientListHeader", "not-fail");

        assertMockEndpointsSatisfied();

        context.stop();

        assertEquals(0, invoked);
    }

    public void testRetryUntilRecipientFailAndNotFail() throws Exception {
        invoked = 0;

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(0);

        template.sendBodyAndHeader("seda:start", "Hello World", "recipientListHeader", "fail,not-fail");

        assertMockEndpointsSatisfied();

        context.stop();

        assertEquals(3, invoked);
    }

    public void testRetryUntilRecipientNotFailAndFail() throws Exception {
        invoked = 0;

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(0);

        template.sendBodyAndHeader("seda:start", "Hello World", "recipientListHeader", "not-fail,fail");

        assertMockEndpointsSatisfied();

        context.stop();

        assertEquals(3, invoked);
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("seda:start")
                    .onException(Exception.class).retryUntil(bean("myRetryBean")).end()
                    .recipientList(header("recipientListHeader"))
                    .to("mock:result");

                from("direct:foo").to("log:foo").to("mock:foo");

                from("direct:fail").to("log:fail", "mock:fail").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    public class MyRetryBean {

        // using bean binding we can bind the information from the exchange to the types we have in our method signature
        public boolean retryUntil(@Header(Exchange.REDELIVERY_COUNTER) Integer counter, @Body String body, @ExchangeException Exception causedBy) {
            // NOTE: counter is the redelivery attempt, will start from 1
            invoked++;

            // we can of course do what ever we want to determine the result but this is a unit test so we end after 3 attempts
            return counter < 3;
        }
    }

}