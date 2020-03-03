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

import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.awaitility.Awaitility.await;

/**
 * Wire tap unit test
 */
public class WireTapShutdownBeanTest extends ContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WireTapShutdownBeanTest.class);

    private static final Exchanger<Void> EXCHANGER = new Exchanger<>();

    @Test
    public void testWireTapShutdown() throws Exception {
        final MyTapBean tapBean = (MyTapBean)context.getRegistry().lookupByName("tap");

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        EXCHANGER.exchange(null);

        // shutdown Camel which should let the inflight wire-tap message route
        // to completion
        context.stop();

        // should allow to shutdown nicely
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals("Hello World", tapBean.getTapped());
        });
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("tap", new MyTapBean());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").wireTap("bean:tap").dynamicUri(false).to("mock:result");
            }
        };
    }

    public static class MyTapBean {

        private String tapped;

        public void tapSomething(String body) throws Exception {
            try {
                EXCHANGER.exchange(null);
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
