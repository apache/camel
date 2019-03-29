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
package org.apache.camel.component.undertow.cloud;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class UndertowServiceCallRouteTest extends CamelTestSupport {

    @Test
    public void testCustomCall() throws Exception {
        Assert.assertEquals("8081", template.requestBody("direct:custom", "hello", String.class));
        Assert.assertEquals("8082", template.requestBody("direct:custom", "hello", String.class));
    }

    @Test
    public void testDefaultSchema() throws Exception {
        try {
            Assert.assertEquals("8081", template.requestBody("direct:default", "hello", String.class));
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof ResolveEndpointFailedException);
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:custom")
                    .serviceCall()
                        .name("myService")
                        .component("undertow")
                        .staticServiceDiscovery()
                            .servers("myService@localhost:8081")
                            .servers("myService@localhost:8082")
                        .endParent();

                from("direct:default")
                    .serviceCall()
                        .name("myService")
                        .staticServiceDiscovery()
                            .servers("myService@localhost:8081")
                            .servers("myService@localhost:8082")
                        .endParent();

                from("undertow:http://localhost:8081")
                    .transform().constant("8081");
                from("undertow:http://localhost:8082")
                    .transform().constant("8082");
            }
        };
    }
}
