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
package org.apache.camel.component.jetty.async;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

/**
 * CAMEL-4795, there should be no exceptions in the logs (before the fix there was a NPE)
 *
 * @version
 */
public class JettyAsyncThrottleTest extends BaseJettyTest {

    @Test
    public void testJettyAsync() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(5);

        template.asyncRequestBody("jetty:http://localhost:{{port}}/myservice", null);
        template.asyncRequestBody("jetty:http://localhost:{{port}}/myservice", null);
        template.asyncRequestBody("jetty:http://localhost:{{port}}/myservice", null);
        template.asyncRequestBody("jetty:http://localhost:{{port}}/myservice", null);
        template.asyncRequestBody("jetty:http://localhost:{{port}}/myservice", null);

        assertMockEndpointsSatisfied();
        
        for (int i = 0; i < 5; i++) {
            Exchange exchange = getMockEndpoint("mock:result").getReceivedExchanges().get(i);
            log.info("Reply " + exchange);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                int port2 = getNextPort();
                int port3 = getNextPort();

                from("jetty:http://localhost:{{port}}/myservice")
                    .removeHeaders("*")
                    .throttle(2).asyncDelayed()
                        .loadBalance().failover()
                            .to("jetty:http://localhost:" + port2 + "/foo")
                            .to("jetty:http://localhost:" + port3 + "/bar")
                        .end()
                    .to("mock:result");

                from("jetty:http://localhost:" + port2 + "/foo")
                    .transform().constant("foo")
                    .to("mock:foo");

                from("jetty:http://localhost:" + port3 + "/bar")
                    .transform().constant("bar")
                    .to("mock:bar");
            }
        };
    }
}