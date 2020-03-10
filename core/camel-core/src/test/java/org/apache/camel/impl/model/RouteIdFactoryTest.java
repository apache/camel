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
package org.apache.camel.impl.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.component.rest.DummyRestProcessorFactory;
import org.apache.camel.impl.RouteIdFactory;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class RouteIdFactoryTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        jndi.bind("dummy-rest-api", new DummyRestProcessorFactory());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.adapt(ExtendedCamelContext.class).setNodeIdFactory(new RouteIdFactory());
                from("direct:start1?timeout=30000").to("mock:result");
                from("direct:start2").to("mock:result");
                rest("/say/hello").get("/bar").to("mock:result");
                rest("/say/hello").get().to("mock:result");
                rest().get("/hello").to("mock:result");
            }
        };
    }

    @Test
    public void testDirectRouteIdWithOptions() {
        assertEquals("start1", context.getRouteDefinitions().get(0).getId());
    }

    @Test
    public void testDirectRouteId() {
        assertEquals("start2", context.getRouteDefinitions().get(1).getId());
    }

    @Test
    public void testRestRouteIdWithVerbUri() {
        assertEquals("get-say-hello-bar", context.getRouteDefinitions().get(2).getId());
    }

    @Test
    public void testRestRouteIdWithoutVerbUri() {
        assertEquals("get-say-hello", context.getRouteDefinitions().get(3).getId());
    }

    @Test
    public void testRestRouteIdWithoutPathUri() {
        assertEquals("get-hello", context.getRouteDefinitions().get(4).getId());
    }

}
