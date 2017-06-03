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
package org.apache.camel.component.hystrix.processor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.HystrixDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HystrixRouteConfigTest extends CamelTestSupport {

    @Test
    public void testHystrix() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testGroupKeyAndThreadPoolKeyConfigFlagsDoNotScrapHystrixConfiguration() throws Exception {
        // dummy route
        RouteBuilder rb = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo")
                    .hystrix()
                        .hystrixConfiguration().groupKey("test1").metricsHealthSnapshotIntervalInMilliseconds(99999).end()
                        .groupKey("test2")
                        // ^^^ should only override the groupKey from the HystrixConfigurationDefinition; 
                        // it should not discard the full HystrixConfigurationDefinition.
                        .to("log:hello")
                    .end();
                
            }
        };
        
        rb.configure();
        
        RouteDefinition route = rb.getRouteCollection().getRoutes().get(0);
        assertEquals(HystrixDefinition.class, route.getOutputs().get(0).getClass());
        
        HystrixConfigurationDefinition config = ((HystrixDefinition) route.getOutputs().get(0)).getHystrixConfiguration();
        assertEquals("test2", config.getGroupKey());
        assertEquals(99999, config.getMetricsHealthSnapshotIntervalInMilliseconds().intValue());
    }

    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .hystrix()
                        .hystrixConfiguration().groupKey("myCamelApp").requestLogEnabled(false).corePoolSize(5).end()
                        .to("direct:foo")
                    .onFallback()
                        .transform().constant("Fallback message")
                    .end()
                    .to("mock:result");

                from("direct:foo")
                    .transform().constant("Bye World");
            }
        };
    }

}
