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

public class FromRestDuplicateTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("dummy-rest", new DummyRestConsumerFactory());
        return jndi;
    }

    public void testDuplicateGet() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    restConfiguration().host("localhost");

                    rest("/users")
                            .get("{id}").to("log:foo")
                            .post().to("log:foo")
                            .get("").to("log:foo")
                            .get("{id}").to("log:foo");

                }
            });
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Duplicate verb detected in rest-dsl: get:{id}", e.getMessage());
        }
    }

    public void testDuplicatePost() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    restConfiguration().host("localhost");

                    rest("/users")
                            .get("{id}").to("log:foo")
                            .post().to("log:foo")
                            .get("").to("log:foo")
                            .put().to("log:foo")
                            .post().to("log:foo");

                }
            });
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Duplicate verb detected in rest-dsl: post", e.getMessage());
        }
    }

}
