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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class RequestScopedBeanComponentTest extends ContextTestSupport {

    @Test
    public void testRequestScope() throws Exception {
        // creates a new instance so the counter starts from 1
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello1", "World1");
        // and on the 2nd call its the same instance (request) so counter is now 2
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello12", "World12");

        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("bean:org.apache.camel.component.bean.MyRequestBean?scope=Request")
                    .to("mock:a")
                    .to("bean:org.apache.camel.component.bean.MyRequestBean?scope=Request")
                    .to("mock:b");
            }
        };
    }
}
