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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ChoicePredicateSimpleHeaderTest extends ContextTestSupport {

    @Test
    public void testAAE() throws Exception {
        getMockEndpoint("mock:aae").expectedMessageCount(1);
        getMockEndpoint("mock:pca").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "Action", "AAE");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPCA() throws Exception {
        getMockEndpoint("mock:aae").expectedMessageCount(0);
        getMockEndpoint("mock:pca").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "Action", "PCA");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testError() throws Exception {
        getMockEndpoint("mock:aae").expectedMessageCount(0);
        getMockEndpoint("mock:pca").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "Action", "Foo");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoHeader() throws Exception {
        getMockEndpoint("mock:aae").expectedMessageCount(0);
        getMockEndpoint("mock:pca").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when().simple("${in.header.Action} == 'AAE'").to("mock:aae").when().simple("${in.header.Action} == 'PCA'").to("mock:pca").otherwise()
                    .to("mock:error");
            }
        };
    }
}
