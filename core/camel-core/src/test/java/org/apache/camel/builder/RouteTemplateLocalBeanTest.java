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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateLocalBeanTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testGlobalBean() throws Exception {
        context.getRegistry().bind("myBar", (Processor) ex -> ex.getMessage().setBody("Global " + ex.getMessage().getBody()));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .routeId("myRoute")
                .add();

        assertEquals(1, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Global World", out);

        context.stop();
    }

    @Test
    public void testLocalBeanInBuilder() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("bean:{{bar}}");
            }
        });

        context.start();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("foo", "one")
                .parameter("bar", "myBar")
                .bind("myBar", (Processor) ex -> ex.getMessage().setBody("Builder " + ex.getMessage().getBody()))
                .routeId("myRoute")
                .add();

        assertEquals(1, context.getRoutes().size());

        Object out = template.requestBody("direct:one", "World");
        assertEquals("Builder World", out);

        context.stop();
    }

}
