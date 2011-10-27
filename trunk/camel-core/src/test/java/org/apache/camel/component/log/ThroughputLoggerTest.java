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
package org.apache.camel.component.log;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ThroughputLoggerTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testSendMessageToLogUsingGroupSize() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("seda:in").to("log:hello?groupSize=2").delay(100).to("mock:result");
            }
        });
        context.start();
        
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        template.sendBody("seda:in", "Hello World");
        template.sendBody("seda:in", "Hello World");
        template.sendBody("seda:in", "Bye World");
        template.sendBody("seda:in", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testSendMessageToLogUsingGroupInterval() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("seda:in").to("log:hello?groupInterval=200&groupDelay=400&groupActiveOnly=false").delay(50).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(20);

        for (int i = 0; i < 20; i++) {
            template.sendBody("seda:in", "Hello World");
        }
        
        assertMockEndpointsSatisfied();
    }
}