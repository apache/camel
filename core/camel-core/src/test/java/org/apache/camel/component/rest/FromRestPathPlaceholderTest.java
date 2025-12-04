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

package org.apache.camel.component.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

public class FromRestPathPlaceholderTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    protected int getExpectedNumberOfRoutes() {
        // routes are inlined
        return 1;
    }

    @Test
    public void testPlaceholder() {
        assertEquals(getExpectedNumberOfRoutes(), context.getRoutes().size());

        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);
        assertEquals("/say/{{mypath}}", rest.getPath());

        // placeholder should be resolved, so we can find the rest endpoint that is a dummy (via seda)
        assertNotNull(context.hasEndpoint("seda://get-say-hello"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getPropertiesComponent().addInitialProperty("mypath", "hello");

                restConfiguration().host("localhost");

                rest("/say/{{mypath}}").get().to("direct:hello");

                from("direct:hello").log("Hello");
            }
        };
    }
}
