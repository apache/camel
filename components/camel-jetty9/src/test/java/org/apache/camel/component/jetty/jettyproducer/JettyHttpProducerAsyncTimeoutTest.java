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
package org.apache.camel.component.jetty.jettyproducer;

import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.junit.Test;

/**
 * @version 
 */
public class JettyHttpProducerAsyncTimeoutTest extends BaseJettyTest {

    private String url = "jetty://http://127.0.0.1:" + getPort() + "/timeout?httpClient.timeout=2000";

    @Test
    public void testTimeout() throws Exception {
        
        // give Jetty time to startup properly
        Thread.sleep(1000);

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:timeout").expectedMessageCount(1);

        template.sendBody("direct:start", null);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:error");
                onException(ExchangeTimedOutException.class).handled(true).to("mock:timeout");

                from("direct:start").to(url).to("mock:result");

                from(url).delay(5000).transform(constant("Bye World"));
            }
        };
    }
}