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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * A simple interceptor routing test
 */
public class InterceptFromSimpleRouteTest extends ContextTestSupport {

    @Test
    public void testIntercept() throws Exception {
        MockEndpoint intercepted = getMockEndpoint("mock:intercepted");
        intercepted.expectedBodiesReceived("Hello London");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("seda:a", "Hello London", "city", "London");
        template.sendBodyAndHeader("seda:a", "Hello Paris", "city", "Paris");

        intercepted.assertIsSatisfied();
        result.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // In Camel 1.4 proceed is default so we must use stop to not
                // route it to the result mock
                interceptFrom().when(header("city").isEqualTo("London")).to("mock:intercepted").stop();
                from("seda:a").to("mock:result");
            }
        };
    }

}
