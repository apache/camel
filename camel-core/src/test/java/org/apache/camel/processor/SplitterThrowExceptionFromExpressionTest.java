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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class SplitterThrowExceptionFromExpressionTest extends ContextTestSupport {

    public void testSplitterAndVerifyException() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(ExpressionEvaluationException.class)
                    .handled(true)
                    .to("mock://error");

                from("direct://start")
                    .split().method(SplitterThrowExceptionFromExpressionTest.class, "splitMe")
                        .to("mock://result")
                    .end();
            }
        };
    }

    public List<String> splitMe(Exchange exchange) throws ExpressionEvaluationException {
        throw new ExpressionEvaluationException(null, exchange, null);
    }

}
