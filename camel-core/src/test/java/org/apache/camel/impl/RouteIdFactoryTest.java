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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.component.rest.DummyRestProcessorFactory;

public class RouteIdFactoryTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        jndi.bind("dummy-rest-api", new DummyRestProcessorFactory());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setNodeIdFactory(new RouteIdFactory());
                from("direct:start1?timeout=30000").to("mock:result");
                from("direct:start2").to("mock:result");
                rest("/say/hello").get("/bar").to("mock:result");
                rest("/say/hello").get().to("mock:result");
                rest().get("/hello").to("mock:result");
            }
        };
    }

    public void testDirectRouteIdWithOptions() {
        assertEquals("start1", context.getRouteDefinitions().get(0).getId());
    }

    public void testDirectRouteId() {
        assertEquals("start2", context.getRouteDefinitions().get(1).getId());
    }

    public void testRestRouteIdWithVerbUri() {
        assertEquals("get-say-hello-bar", context.getRouteDefinitions().get(2).getId());
    }

    public void testRestRouteIdWithoutVerbUri() {
        assertEquals("get-say-hello", context.getRouteDefinitions().get(3).getId());
    }

    public void testRestRouteIdWithoutPathUri() {
        assertEquals("get-hello", context.getRouteDefinitions().get(4).getId());
    }

}