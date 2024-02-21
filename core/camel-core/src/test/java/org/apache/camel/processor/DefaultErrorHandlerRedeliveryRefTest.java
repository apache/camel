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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.errorhandler.RedeliveryPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DefaultErrorHandlerRedeliveryRefTest extends ContextTestSupport {

    private static int counter;

    @Test
    public void testRedeliveryRefTest() throws Exception {
        counter = 0;

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            // expected
        }

        // One call + 2 re-deliveries
        assertEquals(3, counter);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                RedeliveryPolicy policy = new RedeliveryPolicy();
                policy.maximumRedeliveries(2);
                policy.redeliveryDelay(0);
                getCamelContext().getRegistry().bind("myPolicy", policy);

                errorHandler(defaultErrorHandler().redeliveryPolicyRef("myPolicy"));

                from("direct:start").process(new MyProcessor());
            }
        };
    }

    public static class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            counter++;
            throw new Exception("Forced exception by unit test");
        }
    }

}
