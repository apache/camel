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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rest.DummyRestConsumerFactory;
import org.apache.camel.component.rest.DummyRestProcessorFactory;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class RouteIdRestDefinitionTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("dummy-rest", new DummyRestConsumerFactory());
        registry.bind("dummy-rest-api", new DummyRestProcessorFactory());
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start1?timeout=30000").to("mock:result");
                from("direct:start2").to("mock:result");
                rest("/say/hello").get("/bar").id("getSayHelloBar").to("mock:result").get("/bar/{user}").id("getSayHelloBarWithUser").to("mock:result");
            }
        };
    }

    @Test
    public void testSayHelloBar() {
        assertEquals("getSayHelloBar", context.getRouteDefinitions().get(2).getId());
        assertEquals("getSayHelloBarWithUser", context.getRouteDefinitions().get(3).getId());
    }

}
