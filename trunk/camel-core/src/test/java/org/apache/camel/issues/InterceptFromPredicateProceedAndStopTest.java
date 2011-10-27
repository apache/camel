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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Testing http://camel.apache.org/dsl.html
 */
public class InterceptFromPredicateProceedAndStopTest extends ContextTestSupport {

    public void testInterceptorNoPredicate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                interceptFrom().to("mock:test");
                from("seda:order").to("mock:ok");
            }
        });

        MockEndpoint mockTest = getMockEndpoint("mock:test");
        mockTest.expectedBodiesReceived("Camel in Action");

        MockEndpoint mockOk = getMockEndpoint("mock:ok");
        mockOk.expectedBodiesReceived("Camel in Action");

        template.sendBodyAndHeader("seda:order", "Camel in Action", "user", "test");

        mockTest.assertIsSatisfied();
        mockOk.assertIsSatisfied();
    }

    public void testInterceptorNoPredicateAndProceed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                interceptFrom().to("mock:test");
                from("seda:order").to("mock:ok");
            }
        });

        MockEndpoint mockTest = getMockEndpoint("mock:test");
        mockTest.expectedBodiesReceived("Camel in Action");

        MockEndpoint mockOk = getMockEndpoint("mock:ok");
        mockOk.expectedBodiesReceived("Camel in Action");

        template.sendBodyAndHeader("seda:order", "Camel in Action", "user", "test");

        mockTest.assertIsSatisfied();
        mockOk.assertIsSatisfied();
    }

    public void testInterceptorNoPredicateAndStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                interceptFrom().to("mock:test").stop();
                from("seda:order").to("mock:ok");
            }
        });

        MockEndpoint mockTest = getMockEndpoint("mock:test");
        mockTest.expectedBodiesReceived("Camel in Action");

        MockEndpoint mockOk = getMockEndpoint("mock:ok");
        mockOk.expectedMessageCount(0);

        template.sendBodyAndHeader("seda:order", "Camel in Action", "user", "test");

        mockTest.assertIsSatisfied();
        mockOk.assertIsSatisfied();
    }

    public void testInterceptorWithPredicate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                interceptFrom().when(header("user").isEqualTo("test")).to("mock:test");
                from("seda:order").to("mock:ok");
            }
        });

        MockEndpoint mockTest = getMockEndpoint("mock:test");
        mockTest.expectedBodiesReceived("Camel in Action");

        MockEndpoint mockOk = getMockEndpoint("mock:ok");
        mockOk.expectedBodiesReceived("Camel in Action");

        template.sendBodyAndHeader("seda:order", "Camel in Action", "user", "test");

        mockTest.assertIsSatisfied();
        mockOk.assertIsSatisfied();
    }

    public void testInterceptorWithPredicateAndProceed() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                interceptFrom().when(header("user").isEqualTo("test")).to("mock:test");
                from("seda:order").to("mock:ok");
            }
        });

        MockEndpoint mockTest = getMockEndpoint("mock:test");
        mockTest.expectedBodiesReceived("Camel in Action");

        MockEndpoint mockOk = getMockEndpoint("mock:ok");
        mockOk.expectedBodiesReceived("Camel in Action");

        template.sendBodyAndHeader("seda:order", "Camel in Action", "user", "test");

        mockTest.assertIsSatisfied();
        mockOk.assertIsSatisfied();
    }

    public void testInterceptorWithPredicateAndStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                interceptFrom().when(header("user").isEqualTo("test")).to("mock:test").stop();
                from("seda:order").to("mock:ok");
            }
        });

        MockEndpoint mockTest = getMockEndpoint("mock:test");
        mockTest.expectedBodiesReceived("Camel in Action");

        MockEndpoint mockOk = getMockEndpoint("mock:ok");
        mockOk.expectedMessageCount(0);

        template.sendBodyAndHeader("seda:order", "Camel in Action", "user", "test");

        mockTest.assertIsSatisfied();
        mockOk.assertIsSatisfied();
    }

}