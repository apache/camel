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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import static org.apache.camel.builder.ProcessorBuilder.throwException;

/**
 * Unit test to verify delay pattern
 */
public class DeadLetterChannelRedeliveryDelayPatternTest extends ContextTestSupport {

    private static int counter;

    public void testDelayPatternTest() throws Exception {
        counter = 0;

        // We expect the exchange here after 1 delivery and 2 re-deliveries
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);
        mock.message(0).header("CamelRedelivered").isEqualTo(Boolean.TRUE);
        mock.message(0).header("CamelRedeliveryCounter").isEqualTo(3);

        long start = System.currentTimeMillis();
        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (Exception e) {
            // expected
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
        }
        long delta = System.currentTimeMillis() - start;
        assertTrue("Should be slower", delta > 1000);

        assertMockEndpointsSatisfied();

        assertEquals(3, counter);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").delayPattern("0:250;2:500").maximumRedeliveries(3)
                    .onRedelivery(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            counter++;
                        }
                    }));
                // we don't want DLC to handle the Exception
                onException(Exception.class).handled(false);

                from("direct:start").process(throwException(new Exception("Forced exception by unit test")));
            }
        };
    }

}