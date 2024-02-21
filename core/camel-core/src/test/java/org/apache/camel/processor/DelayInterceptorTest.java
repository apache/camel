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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Delay interceptor unit test.
 */
public class DelayInterceptorTest extends ContextTestSupport {

    @Test
    public void testDelayer() throws Exception {
        StopWatch watch = new StopWatch();
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message #" + i);
        }
        long delta = watch.taken();
        assertTrue(delta > 100, "Should not be that fast to run: " + delta);
        // some OS boxes are slow
        assertTrue(delta < 5000, "Should not take that long to run: " + delta);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: e1
            public void configure() throws Exception {
                // configure delayer for each step 10 millis
                getContext().setDelayer(10L);

                // regular routes here

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // do nothing
                    }
                }).to("mock:result");
            }
        };
    }

}
