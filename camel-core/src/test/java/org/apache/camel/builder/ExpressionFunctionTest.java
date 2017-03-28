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

package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.builder.ExpressionBuilder.messageExpression;

/**
 * @version 
 */
public class ExpressionFunctionTest extends ContextTestSupport {

    public void testTransform() throws Exception {
        MockEndpoint functionMock = getMockEndpoint("mock:function");
        functionMock.expectedMessageCount(1);
        functionMock.expectedBodyReceived().constant("function");

        MockEndpoint inFunctionMock = getMockEndpoint("mock:inFunction");
        inFunctionMock.expectedMessageCount(1);
        inFunctionMock.expectedBodyReceived().constant("inFunction");

        MockEndpoint inFunction2Mock = getMockEndpoint("mock:inFunction2");
        inFunction2Mock.expectedMessageCount(1);
        inFunction2Mock.expectedBodyReceived().constant("inFunction2");

        template.sendBodyAndHeader("direct:function", "Hello World", "type", "function");
        template.sendBodyAndHeader("direct:inFunction", "Hello World", "type", "inFunction");
        template.sendBodyAndHeader("direct:inFunction2", "Hello World", "type", "inFunction2");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:function")
                    .transform().message(m -> m.getExchange().getIn().getHeader("type"))
                    .to("mock:function");
                from("direct:inFunction")
                    .transform().message(m -> m.getHeader("type"))
                    .to("mock:inFunction");
                from("direct:inFunction2")
                    .transform(messageExpression(m -> m.getHeader("type")))
                    .to("mock:inFunction2");
            }
        };
    }
}
