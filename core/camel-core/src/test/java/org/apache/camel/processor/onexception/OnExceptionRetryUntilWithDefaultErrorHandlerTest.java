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
package org.apache.camel.processor.onexception;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
import org.apache.camel.Header;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 * Unit test for the retry until predicate
 */
public class OnExceptionRetryUntilWithDefaultErrorHandlerTest extends ContextTestSupport {

    private static int invoked;

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("myRetryHandler", new MyRetryBean());
        return jndi;
    }

    @Test
    public void testRetryUntil() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // as its based on a unit test we do not have any delays between
                // and do not log the stack trace
                errorHandler(defaultErrorHandler().maximumRedeliveries(1).logStackTrace(false));

                onException(MyFunctionalException.class).retryWhile(method("myRetryHandler")).redeliveryDelay(0).handled(true).transform().constant("Sorry").stop();

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

    public class MyRetryBean {

        // using bean binding we can bind the information from the exchange to
        // the types we have in our method signature
        public boolean retry(@Header(Exchange.REDELIVERY_COUNTER) Integer counter, @Body String body, @ExchangeException Exception causedBy) {
            // NOTE: counter is the redelivery attempt, will start from 1
            invoked++;

            assertEquals("Hello World", body);
            assertTrue(causedBy instanceof MyFunctionalException);

            // we can of course do what ever we want to determine the result but
            // this is a unit test so we end after 3 attempts
            return counter < 3;
        }
    }

}
