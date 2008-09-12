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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test to verify that error handling using thread() pool also works as expected.
 */
public class BelasThreadErrorHandlerIssue794Test extends ContextTestSupport {
    private static int counter;

    public void testThreadErrorHandlerRedeliveryNoThread() throws Exception {
        counter = 0;

        // We expect the exchange here after 1 delivery and 2 re-deliveries
        MockEndpoint mock = getMockEndpoint("mock:noThread");
        mock.expectedMessageCount(1);
        mock.message(0).header("org.apache.camel.Redelivered").isEqualTo(Boolean.TRUE);
        mock.message(0).header("org.apache.camel.RedeliveryCounter").isEqualTo(2);

        try {
            template.sendBody("direct:inNoThread", "Hello World");
            fail("Should have thrown a RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
            // expected
        }

        mock.assertIsSatisfied();
        assertEquals(3, counter); // One call + 2 re-deliveries
    }

    public void testThreadErrorHandlerRedeliveryBeforeThread() throws Exception {
        counter = 0;

        // We expect the exchange here after 1 delivery and 2 re-deliveries
        MockEndpoint mock = getMockEndpoint("mock:beforeThread");
        mock.expectedMessageCount(1);
        mock.message(0).header("org.apache.camel.Redelivered").isEqualTo(Boolean.TRUE);
        mock.message(0).header("org.apache.camel.RedeliveryCounter").isEqualTo(2);

        try {
            template.sendBody("direct:inBeforeThread", "Hello World");
            fail("Should have thrown a RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
            // expected
        }

        mock.assertIsSatisfied();
    }

    public void testThreadErrorHandlerCallBeforeThread() throws Exception {
        counter = 0;

        try {
            template.sendBody("direct:inBeforeThread", "Hello World");
            fail("Should have thrown a RuntimeCamelException");
        } catch (RuntimeCamelException e) {
            assertEquals("Forced exception by unit test", e.getCause().getMessage());
            // expected
        }

        assertEquals(3, counter); // One call + 2 re-deliveries
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:inNoThread")
                    .errorHandler(deadLetterChannel("mock:noThread").maximumRedeliveries(2))
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            counter++;
                            throw new Exception("Forced exception by unit test");
                        }
                    });

                from("direct:inBeforeThread")
                    .errorHandler(deadLetterChannel("mock:beforeThread").maximumRedeliveries(2))
                    .thread(2)
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            counter++;
                            throw new Exception("Forced exception by unit test");
                        }
                    });
            }
        };
    }

}
