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
package org.apache.camel.builder;

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RouteTemplateOnExceptionTest extends ContextTestSupport {

    @Test
    public void testOnException() throws Exception {
        context.addRouteFromTemplate("myRoute", "myTemplate", Map.of("foo", "start"));

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        Exchange out = fluentTemplate.withBody("Hello World").to("direct:start").send();
        Assertions.assertTrue(out.isRollbackOnly());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOnExceptionPrefixId() throws Exception {
        context.addRouteFromTemplate("myRoute", "myTemplate", "myPrefix", Map.of("foo", "start"));

        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        Exchange out = fluentTemplate.withBody("Bye World").to("direct:start").send();
        Assertions.assertTrue(out.isRollbackOnly());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate").templateParameter("foo")
                        .from("direct:{{foo}}")
                        .onException(Exception.class)
                            .to("mock:error")
                            .markRollbackOnly()
                        .end()
                        .throwException(new IllegalArgumentException("Forced"))
                        .to("mock:result");
            }
        };
    }
}
