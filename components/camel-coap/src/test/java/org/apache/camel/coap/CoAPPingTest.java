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
import org.junit.Test;

public class CoAPPingTest extends CoAPTestSupport {

    @Produce(uri = "direct:start")
    protected ProducerTemplate sender;
    
    @Test
    public void testCoAP() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(true);
        sender.sendBodyAndHeader("Hello", CoAPConstants.COAP_METHOD, CoAPConstants.METHOD_PING);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception { 
                fromF("coap://localhost:%d/TestResource", PORT)
                    .to("log:exch")
                    .transform(body().convertTo(Boolean.class))
                    .to("log:exch");
                
                from("direct:start")
                    .toF("coap://localhost:%d/TestResource", PORT)
                    .to("mock:result");
            }
        };
    }
}
