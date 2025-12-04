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

package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PollEnrichVariableErrorTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testThrowException() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .pollEnrich()
                        .constant("seda:foo")
                        .timeout(1000)
                        .variableReceive("bye")
                        .to("mock:result");
            }
        });
        context.start();

        template.send("seda:foo", e -> {
            e.getMessage().setBody("Bye World");
            e.setException(new IllegalArgumentException());
        });

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertTrue(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testStop() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .pollEnrich()
                        .constant("seda:foo")
                        .timeout(1000)
                        .variableReceive("bye")
                        .to("mock:result");
            }
        });
        context.start();

        SedaEndpoint se = context.getEndpoint("seda:foo", SedaEndpoint.class);
        Exchange ex = se.createExchange();
        ex.getMessage().setBody("Bye World");
        ex.setRouteStop(true);
        se.getQueue().add(ex);

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertTrue(out.isRouteStop());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRollbackOnly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:receive")
                        .pollEnrich()
                        .constant("seda:foo")
                        .timeout(1000)
                        .variableReceive("bye")
                        .to("mock:result");
            }
        });
        context.start();

        SedaEndpoint se = context.getEndpoint("seda:foo", SedaEndpoint.class);
        Exchange ex = se.createExchange();
        ex.getMessage().setBody("Bye World");
        ex.setRollbackOnly(true);
        se.getQueue().add(ex);

        getMockEndpoint("mock:result").expectedMessageCount(0);
        Exchange out = template.request("direct:receive", e -> e.getMessage().setBody("World"));
        Assertions.assertFalse(out.isFailed());
        Assertions.assertFalse(out.hasVariables());
        Assertions.assertTrue(out.isRollbackOnly());
        Assertions.assertEquals("Bye World", out.getMessage().getBody());
        assertMockEndpointsSatisfied();
    }
}
