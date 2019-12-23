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
package org.apache.camel.component.jetty.rest.producer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JettyRestProducerInvalidApiDocTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testInvalidPath() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use localhost with the given port
                restConfiguration().component("jetty").host("localhost");

                from("direct:start").to("rest:get:api/hello/unknown/{name}?apiDoc=hello-api.json");

            }
        });
        try {
            context.start();
            fail("Should fail");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("Swagger api-doc does not contain operation for get:/api/hello/unknown/{name}", iae.getMessage());
        }
    }

    @Test
    public void testInvalidQuery() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use localhost with the given port
                restConfiguration().component("jetty").host("localhost").producerApiDoc("hello-api.json");

                from("direct:start").to("rest:get:api/bye/?unknown={name}");

            }
        });
        try {
            context.start();
            fail("Should fail");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause().getCause());
            assertEquals("Swagger api-doc does not contain query parameter name for get:/api/bye", iae.getMessage());
        }
    }
}
