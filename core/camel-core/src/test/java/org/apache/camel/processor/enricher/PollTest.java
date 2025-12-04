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

package org.apache.camel.processor.enricher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PollTest extends ContextTestSupport {

    protected MockEndpoint mock;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        mock = getMockEndpoint("mock:mock");
    }

    // -------------------------------------------------------------
    // InOnly routes
    // -------------------------------------------------------------

    @Test
    public void testPoll() throws InterruptedException {
        template.sendBody("seda:foo1", "blah");

        mock.expectedBodiesReceived("blah");

        template.sendBody("direct:enricher-test-1", "test");

        mock.assertIsSatisfied();
    }

    @Test
    public void testPollWithTimeout() throws InterruptedException {
        // this first try there is no data so we timeout
        mock.expectedBodiesReceived("blah");

        template.sendBody("direct:enricher-test-2", "test");
        // not expected data so we are not happy
        mock.assertIsNotSatisfied();

        // now send it and try again
        mock.reset();
        template.sendBody("seda:foo2", "blah");
        template.sendBody("direct:enricher-test-2", "test");
        mock.assertIsSatisfied();
    }

    @Test
    public void testPollNoTimeout() throws InterruptedException {
        // use another thread to send it a bit later
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    // ignore
                }
                template.sendBody("seda:foo3", "blah");
            }
        });

        StopWatch watch = new StopWatch();
        mock.expectedBodiesReceived("blah");

        t.start();
        template.sendBody("direct:enricher-test-3", "test");
        // should take approx 1 sec to complete as the other thread is sending a
        // bit later and we wait
        mock.assertIsSatisfied();
        long delta = watch.taken();
        assertTrue(delta > 150, "Should take approx 0.25 sec: was " + delta);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:enricher-test-1").poll("seda:foo1").to("mock:mock");

                from("direct:enricher-test-2").poll("seda:foo2", 1000).to("mock:mock");

                from("direct:enricher-test-3").poll("seda:foo3", -1).to("mock:mock");
            }
        };
    }
}
