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
package org.apache.camel.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class InflightRepositoryRouteTest extends ContextTestSupport {

    private final CountDownLatch latch = new CountDownLatch(1);

    public void testInflight() throws Exception {
        assertEquals(0, context.getInflightRepository().size());

        template.asyncSendBody("direct:start", "Hello World");
        latch.await(5, TimeUnit.SECONDS);

        assertEquals(1, context.getInflightRepository().size());

        // wait to be sure its done
        Thread.sleep(2000);

        assertEquals(0, context.getInflightRepository().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                latch.countDown();
                            }
                        }).delay(1000).to("mock:result");
            }
        };
    }
}
