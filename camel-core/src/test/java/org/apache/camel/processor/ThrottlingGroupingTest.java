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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ThrottlingGroupingTest extends ContextTestSupport {

    public void testGroupingWithSingleConstant() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Bye World");
        getMockEndpoint("mock:dead").expectedBodiesReceived("Kaboom");

        template.sendBodyAndHeader("seda:a", "Kaboom", "max", null);
        template.sendBodyAndHeader("seda:a", "Hello World", "max", 2);
        template.sendBodyAndHeader("seda:a", "Bye World", "max", 2);

        assertMockEndpointsSatisfied();
    }
    
    public void testGroupingWithDynamicHeaderExpression() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result2").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:dead").expectedBodiesReceived("Kaboom", "Saloon");
        getMockEndpoint("mock:resultdynamic").expectedBodiesReceived("Hello Dynamic World", "Bye Dynamic World");
        
        Map<String, Object> headers = new HashMap<String, Object>();

        template.sendBodyAndHeaders("seda:a", "Kaboom", headers);
        template.sendBodyAndHeaders("seda:a", "Saloon", headers);
        
        headers.put("max", "2");
        template.sendBodyAndHeaders("seda:a", "Hello World", headers);
        template.sendBodyAndHeaders("seda:b", "Bye World", headers);
        headers.put("max", "2");
        headers.put("key", "1");
        template.sendBodyAndHeaders("seda:c", "Hello Dynamic World", headers);
        headers.put("key", "2");
        template.sendBodyAndHeaders("seda:c", "Bye Dynamic World", headers);
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("seda:a").throttle(1, header("max")).to("mock:result");
                from("seda:b").throttle(2, header("max")).to("mock:result2");
                from("seda:c").throttle(header("key"), header("max")).timePeriodMillis(2000).to("mock:resultdynamic");
            }
        };
    }
}
