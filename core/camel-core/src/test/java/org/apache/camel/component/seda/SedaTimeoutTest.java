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
package org.apache.camel.component.seda;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SedaTimeoutTest extends ContextTestSupport {
    private int timeout = 100;

    @Test
    public void testSedaNoTimeout() throws Exception {
        Future<String> out = template.asyncRequestBody("seda:foo", "World", String.class);
        assertEquals("Bye World", out.get());
    }

    @Test
    public void testSedaTimeout() {
        Future<String> out = template.asyncRequestBody("seda:foo?timeout=" + timeout, "World", String.class);

        ExecutionException e = assertThrows(ExecutionException.class, out::get, "Should have thrown an exception");

        assertIsInstanceOf(CamelExecutionException.class, e.getCause());
        assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause().getCause());

        SedaEndpoint se = (SedaEndpoint) context.getRoute("seda").getEndpoint();
        assertNotNull(se, "Consumer endpoint cannot be null");
        assertEquals(0, se.getCurrentQueueSize(), "Timeout Exchanges should be removed from queue");
    }

    @Test
    public void testSedaTimeoutWithStoppedRoute() throws Exception {
        context.getRouteController().stopRoute("seda");
        timeout = 500;
        testSedaTimeout();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("seda").to("mock:before").delay(250).transform(body().prepend("Bye "))
                        .to("mock:result");
            }
        };
    }
}
