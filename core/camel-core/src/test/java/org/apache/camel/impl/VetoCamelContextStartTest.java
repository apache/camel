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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.support.LifecycleStrategySupport;
import org.junit.Test;

/**
 *
 */
public class VetoCamelContextStartTest extends ContextTestSupport {

    private LifecycleStrategy veto = new MyVeto();

    @Test
    public void testVetoCamelContextStart() throws Exception {
        // context is veto'ed but appears as started
        assertEquals(false, context.getStatus().isStarted());
        assertEquals(true, context.getStatus().isStopped());
        assertEquals(0, context.getRoutes().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addLifecycleStrategy(veto);
        return context;
    }

    private class MyVeto extends LifecycleStrategySupport {

        @Override
        public void onContextStart(CamelContext context) throws VetoCamelContextStartException {
            // we just want camel context to not startup, but do not rethrow
            // exception
            throw new VetoCamelContextStartException("Forced", context, false);
        }

    }
}
