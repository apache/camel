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
package org.apache.camel.itest.jms2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Note: these tests offer only a naive check of the deliveryDelay functionality as they check the
 * test duration. There is no guarantee that the cause for the delay is actually the deliveryDelay
 * feature per se and not, for instance, caused by bug on the message broker or an overloaded scheduler
 * taking a long time to handle this test workload. Nonetheless, it can still be useful for investigating
 * bugs which is why we keep them here.
 */
@Isolated("These tests are highly susceptible to flakiness as they verify the results based on duration - which can vary a LOT in loaded systems")
public class Jms2DeliveryDelayTest extends BaseJms2TestSupport {

    @Test
    void testInOnlyWithDelay() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        StopWatch watch = new StopWatch();
        template.sendBody("jms:topic:foo?deliveryDelay=1000", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        assertTrue(watch.taken() >= 1000, "Should take at least 1000 millis");
    }

    @Test
    void testInOutWithDelay() {
        StopWatch watch = new StopWatch();
        template.requestBody("jms:topic:foo?deliveryDelay=1000", "Hello World");
        assertTrue(watch.taken() >= 1000, "Should take at least 1000 millis");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:topic:foo")
                        .to("mock:result");
            }
        };
    }
}
