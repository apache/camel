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
package org.apache.camel.coap;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.junit.Test;

public class CoAPPingTest extends CamelTestSupport {
    static final int PORT = AvailablePortFinder.getNextAvailable();
    
    @Produce(uri = "direct:start")
    protected ProducerTemplate sender;
    
    @Test
    public void testCoAP() throws Exception {
        NetworkConfig.createStandardWithoutFile();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(true);
        sender.sendBody("Hello");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception { 
                from("coap://localhost:" + PORT + "/TestResource?coapMethod=PING")
                    .to("log:exch")
                    .transform(body().convertTo(Boolean.class))
                    .to("log:exch");
                
                from("direct:start").to("coap://localhost:" + PORT + "/TestResource?coapMethod=PING").to("mock:result");
            }
        };
    }
}
