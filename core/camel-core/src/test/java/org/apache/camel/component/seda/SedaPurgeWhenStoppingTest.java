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
package org.apache.camel.component.seda;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SedaPurgeWhenStoppingTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final CountDownLatch latch2 = new CountDownLatch(1);

    @Test
    public void testPurgeWhenStopping() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        for (int i = 0; i < 100; i++) {
            template.sendBody("seda:foo", "Message " + i);
        }

        context.getRouteController().startRoute("myRoute");
        latch.await(2, TimeUnit.SECONDS);
        context.getRouteController().stopRoute("myRoute");
        latch2.countDown();

        mock.setAssertPeriod(500);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?purgeWhenStopping=true").routeId("myRoute").noAutoStartup().process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        latch.countDown();
                        latch2.await(2, TimeUnit.SECONDS);
                    }
                }).to("mock:result");
            }
        };
    }
}
