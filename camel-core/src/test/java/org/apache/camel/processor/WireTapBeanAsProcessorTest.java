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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

import static org.awaitility.Awaitility.await;

/**
 * Wire tap unit test
 *
 * @version 
 */
public class WireTapBeanAsProcessorTest extends ContextTestSupport {
    private MyBean myBean = new MyBean();
    private MockEndpoint result;

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("tap", myBean);
        return jndi;
    }

    public void testBeanAsProcessor() throws Exception {
        assertNull(myBean.getTapped());

        result.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals("World", myBean.getTapped());
        });
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        result = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("log:foo")
                    .wireTap("bean:tap")
                    .transform(body().prepend("Bye "))
                    .to("mock:result");
            }
        };
    }

    public static class MyBean implements Processor {

        private String tapped;

        @Override
        public void process(Exchange exchange) throws Exception {
            tapped = exchange.getIn().getBody(String.class);
        }

        public String getTapped() {
            return tapped;
        }
    }
}