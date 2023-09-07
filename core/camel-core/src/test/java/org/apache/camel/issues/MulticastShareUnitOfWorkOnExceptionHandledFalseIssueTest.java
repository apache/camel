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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MulticastShareUnitOfWorkOnExceptionHandledFalseIssueTest extends ContextTestSupport {

    @Test
    public void testMulticast() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:start", "Hello World"),
                "Should throw exception");

        IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
        assertEquals("Forced", cause.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(false).to("mock:a");

                from("direct:start").multicast().shareUnitOfWork().stopOnException().to("direct:b").end().to("mock:result");

                from("direct:b").to("mock:b").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
