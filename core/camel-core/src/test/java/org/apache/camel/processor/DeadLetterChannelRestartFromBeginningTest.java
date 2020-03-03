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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RecipientList;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

/**
 *
 */
public class DeadLetterChannelRestartFromBeginningTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("retryBean", new RetryBean());
        return jndi;
    }

    @Test
    public void testRestartFromBeginning() throws Exception {
        // 1 original + 4 redeliveries
        getMockEndpoint("mock:start").expectedBodiesReceived("Camel", "Camel", "Camel", "Camel", "Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel");

        // use fire and forget
        template.sendBody("seda:start", "Camel");

        setAssertPeriod(500);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use the DLQ and let the retryBean handle this
                errorHandler(deadLetterChannel("bean:retryBean").useOriginalMessage());

                // the seda:start could be any other kind of fire and forget
                // endpoint
                from("seda:start").to("log:start", "mock:start").transform(body().prepend("Hello ")).process(new Processor() {
                    private int counter;

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        // fail the first 3 times
                        if (counter++ <= 3) {
                            throw new IllegalArgumentException("Damn");
                        }
                    }
                }).to("mock:result");
            }
        };
    }

    /**
     * Bean used as dead letter queue, that decides what to do with the message
     */
    public static class RetryBean {

        // use recipient list to decide what to do with the message
        @RecipientList
        public String handleError(Exchange exchange) {
            // store a property on the exchange with the number of total
            // attempts
            int attempts = exchange.getProperty("attempts", 0, int.class);
            attempts++;
            exchange.setProperty("attempts", attempts);

            // we want to retry at most 4 times
            if (attempts <= 4) {
                return "seda:start";
            } else {
                // okay we give up its a poison message
                return "log:giveup";
            }
        }
    }

}
