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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evicting the producer of a singleton endpoint from a producer cache must not stop the endpoint when it is in use by
 * the routes, but stops a dynamic endpoint that is not in use.
 */
public class ProducerCacheEvictEndpointInUseTest extends ContextTestSupport {

    @Test
    public void testEvictDoesNotStopEndpointInUse() throws Exception {
        template.sendBodyAndHeader("direct:start", "A", "uri", "seda:foo");
        // the cache holds one producer, so this evicts the producer of seda:foo
        template.sendBodyAndHeader("direct:start", "B", "uri", "seda:bar");
        // the eviction is cleaned up on the next use of the cache
        template.sendBodyAndHeader("direct:start", "C", "uri", "seda:bar");

        Endpoint foo = context.hasEndpoint("seda:foo");
        assertTrue(ServiceHelper.isStarted(foo), "seda:foo is used by a route and must not be stopped");
    }

    @Test
    public void testEvictStopsDynamicEndpointNotInUse() throws Exception {
        // seda:dynamic is only used by toD, so it is a dynamic endpoint
        template.sendBodyAndHeader("direct:start", "A", "uri", "seda:dynamic");
        template.sendBodyAndHeader("direct:start", "B", "uri", "seda:bar");
        template.sendBodyAndHeader("direct:start", "C", "uri", "seda:bar");

        Endpoint dynamic = context.hasEndpoint("seda:dynamic");
        assertFalse(ServiceHelper.isStarted(dynamic), "the dynamic endpoint not in use should be stopped to free resources");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").toD("${header.uri}", 1);

                from("seda:foo").to("mock:foo");
                from("seda:bar").to("mock:bar");
            }
        };
    }
}
