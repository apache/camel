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
package org.apache.camel.component.bean.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class DefaultMethodCalledFromSimpleExpressionTest extends ContextTestSupport {

    private static final String DEFAULT_METHOD_CONTENT = "A.defaultMethod() has been called";

    @Test
    public void testDefaultMethodFromSimpleExpression() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived(DEFAULT_METHOD_CONTENT);

        template.sendBodyAndProperty("direct:defaultMethod", "", "myObject", new B() {
        });

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:defaultMethod").setBody(simple("${exchangeProperty.myObject.defaultMethod}")).to("mock:result");
            }
        };
    }

    public interface A {
        default String defaultMethod() {
            return DEFAULT_METHOD_CONTENT;
        }
    }

    public interface B extends A {
    }
}
