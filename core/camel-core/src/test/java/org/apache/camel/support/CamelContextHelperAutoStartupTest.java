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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelContextHelperAutoStartupTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.setAutoStartupExcludePattern("excludedById,direct://excludedByUri");
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:auto").routeId("auto").to("mock:result");
                from("direct:manual").routeId("manual").autoStartup(false).to("mock:result");
                from("direct:byId").routeId("excludedById").to("mock:result");
                from("direct:excludedByUri").routeId("byUri").to("mock:result");
            }
        };
    }

    @Test
    public void testIsAutoStartup() {
        assertTrue(CamelContextHelper.isAutoStartup(context.getRoute("auto")));
        assertFalse(CamelContextHelper.isAutoStartup(context.getRoute("manual")));
        assertFalse(CamelContextHelper.isAutoStartup(context.getRoute("excludedById")));
        assertFalse(CamelContextHelper.isAutoStartup(context.getRoute("byUri")));
    }
}
