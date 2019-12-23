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
package org.apache.camel.processor.dynamicrouter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.DynamicRouter;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class DynamicRouterConcurrentPOJOTest extends ContextTestSupport {

    private static final int COUNT = 100;

    @Test
    public void testConcurrentDynamicRouter() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(COUNT);
        getMockEndpoint("mock:b").expectedMessageCount(COUNT);

        Thread sendToSedaA = createSedaSenderThread("seda:a");
        Thread sendToSedaB = createSedaSenderThread("seda:b");

        sendToSedaA.start();
        sendToSedaB.start();

        assertMockEndpointsSatisfied();
    }

    private Thread createSedaSenderThread(final String seda) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < COUNT; i++) {
                    template.sendBody(seda, "Message from " + seda);
                }
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("seda:a").bean(new MyDynamicRouterPojo("mock:a"));
                from("seda:b").bean(new MyDynamicRouterPojo("mock:b"));
            }
        };
    }

    public class MyDynamicRouterPojo {

        private final String target;

        public MyDynamicRouterPojo(String target) {
            this.target = target;
        }

        @DynamicRouter
        public String route(@Header(Exchange.SLIP_ENDPOINT) String previous) {
            if (previous == null) {
                return target;
            } else {
                return null;
            }
        }
    }
}
