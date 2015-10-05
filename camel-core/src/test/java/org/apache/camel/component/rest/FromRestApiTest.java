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
package org.apache.camel.component.rest;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.RestDefinition;

public class FromRestApiTest extends ContextTestSupport {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        jndi.bind("dummy-rest-api", new DummyRestProcessorFactory());
        return jndi;
    }

    public void testFromRestModel() throws Exception {
        assertEquals(1, context.getRestDefinitions().size());
        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);
        assertEquals("/say/hello", rest.getPath());
        assertEquals(1, rest.getVerbs().size());
        ToDefinition to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getTo());
        assertEquals("log:hello", to.getUri());

        // should be 2 routes
        assertEquals(2, context.getRoutes().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().host("localhost").component("dummy-rest").apiContextPath("/api");

                rest("/say/hello")
                    .get().to("log:hello");
            }
        };
    }
}
