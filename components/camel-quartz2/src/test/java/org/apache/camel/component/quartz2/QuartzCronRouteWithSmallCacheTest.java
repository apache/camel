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
package org.apache.camel.component.quartz2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Endpoints are stored in a LRU list with a default capacity of 1000. If the list is full,
 * then endpoints are removed and should be recreated.
 * <p/>
 * We simulate this behavior with a capacity of 1 element.
 */
public class QuartzCronRouteWithSmallCacheTest extends BaseQuartzTest {

    private final CountDownLatch latch = new CountDownLatch(3);

    @Test
    public void testQuartzCronRouteWithSmallCache() throws Exception {
        boolean wait = latch.await(10, TimeUnit.SECONDS);
        assertTrue(wait);
        assertTrue("Quartz should trigger at least 3 times", latch.getCount() <= 0);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getProperties().put(Exchange.MAXIMUM_ENDPOINT_CACHE_SIZE, "1");
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:foo").to("log:foo");

                from("quartz2://myGroup/myTimerName?cron=0/2+*+*+*+*+?").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        latch.countDown();
                        template.sendBody("direct:foo", "Quartz triggered");
                    }
                });
            }
        };
    }
}