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
package org.apache.camel.itest.issues;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class IntercepFromAndStrategyTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;
    
    @EndpointInject(uri = "mock:intercepted")
    protected MockEndpoint interceptedEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void strategyTest() throws Exception {
        resultEndpoint.expectedBodiesReceived("Bla Bla Bla");
        interceptedEndpoint.expectedBodiesReceived("Bla Bla Bla");
        template.sendBody("direct:start", "Bla Bla Bla");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                // add a dummy strategy
                // removing this line the test works
                context.addInterceptStrategy(new DummyInterceptor());
                // intercet from
                interceptFrom("direct:start").log("Intercepted").to("mock:intercepted");

                from("direct:start").to("mock:result");
            }
        };
    }

    

}
