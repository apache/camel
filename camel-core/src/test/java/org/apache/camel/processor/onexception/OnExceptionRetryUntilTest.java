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
package org.apache.camel.processor.onexception;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
import org.apache.camel.Header;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 * Unit test for the retry until predicate
 */
public class OnExceptionRetryUntilTest extends ContextTestSupport {

    private static int invoked;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myRetryHandler", new MyRetryBean());
        return jndi;
    }

    @Test
    public void testRetryUntil() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // as its based on a unit test we do not have any delays between and do not log the stack trace
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(1).redeliveryDelay(0).logStackTrace(false));

                // START SNIPPET: e1
                // we want to use a predicate for retries so we can determine in our bean
                // when retry should stop, notice it will overrule the global error handler
                // where we defined at most 1 redelivery attempt. Here we will continue until
                // the predicate returns false
                onException(MyFunctionalException.class)
                        .retryWhile(method("myRetryHandler"))
                        .handled(true)
                        .transform().constant("Sorry");
                // END SNIPPET: e1

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new MyFunctionalException("Sorry you cannot do this");
                    }
                });
            }
        });

        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("Sorry", out);
        assertEquals(3, invoked);
    }

    // START SNIPPET: e2
    public class MyRetryBean {

        // using bean binding we can bind the information from the exchange to the types we have in our method signature
        public boolean retry(@Header(Exchange.REDELIVERY_COUNTER) Integer counter, @Body String body, @ExchangeException Exception causedBy) {
            // NOTE: counter is the redelivery attempt, will start from 1
            invoked++;

            assertEquals("Hello World", body);
            assertTrue(causedBy instanceof MyFunctionalException);

            // we can of course do what ever we want to determine the result but this is a unit test so we end after 3 attempts
            return counter < 3;
        }
    }
    // END SNIPPET: e2

}