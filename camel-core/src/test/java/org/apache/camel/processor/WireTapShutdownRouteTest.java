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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;

/**
 * Wire tap unit test
 */
public class WireTapShutdownRouteTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WireTapShutdownRouteTest.class);

    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public void testWireTapShutdown() throws Exception {
        final MyTapBean tapBean = (MyTapBean) context.getRegistry().lookupByName("tap");

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        LATCH.countDown();

        // shutdown Camel which should let the inlfight wire-tap message route to completion
        context.stop();

        // should allow to shutdown nicely
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals("Hello World", tapBean.getTapped());
        });
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("tap", new MyTapBean());
        return jndi;
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").routeId("foo")
                    .wireTap("direct:tap")
                    .to("mock:result");

                from("direct:tap").routeId("bar")
                    .to("bean:tap");
            }
        };
    }

    public static class MyTapBean {

        private String tapped;

        public void tapSomething(String body) throws Exception {
            try {
                LATCH.await(5, TimeUnit.SECONDS);
                Thread.sleep(100);
            } catch (Exception e) {
                fail("Should not be interrupted");
            }
            LOG.info("Wire tapping: {}", body);
            tapped = body;
        }

        public String getTapped() {
            return tapped;
        }
    }
}