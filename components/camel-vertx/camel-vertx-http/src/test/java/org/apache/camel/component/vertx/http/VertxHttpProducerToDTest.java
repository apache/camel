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
package org.apache.camel.component.vertx.http;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxHttpProducerToDTest extends VertxHttpTestSupport {

    @Test
    public void testStatic() {
        String out = template.requestBody("direct:static", null, String.class);
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testStatic2() {
        String out = template.requestBody("direct:static2", null, String.class);
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testDynamic() {
        String out = template.requestBody("direct:dynamic", null, String.class);
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testDynamic2() {
        String out = template.requestBody("direct:dynamic2", null, String.class);
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testDynamic3() {
        String out = template.requestBody("direct:dynamic3", null, String.class);
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testDynamic4() {
        String out = template.requestBody("direct:dynamic4", null, String.class);
        Assertions.assertEquals("Hello World", out);
    }

    @Test
    public void testDynamic5() {
        String out = template.requestBody("direct:dynamic5", null, String.class);
        Assertions.assertEquals("Hello World", out);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:static")
                        .toD(getProducerUri() + "/hello");

                from("direct:static2")
                        .setHeader("CamelHttpUri", constant(getTestServerUrl() + "/hello"))
                        .to("vertx-http:does-not-exist");

                from("direct:dynamic")
                        .setHeader("foo", constant("/hello"))
                        .toD(getProducerUri() + "${header.foo}");

                from("direct:dynamic2")
                        .setHeader("bar", constant(getTestServerUrl() + "/hello"))
                        .toD("vertx-http:${header.bar}");

                from("direct:dynamic3")
                        .setHeader("CamelHttpUri", constant(getTestServerUrl() + "/hello"))
                        // will use regular http component
                        .toD("${header.CamelHttpUri}");

                from("direct:dynamic4")
                        .setHeader("CamelHttpUri", constant(getTestServerUrl() + "/hello"))
                        // compare with regular http component
                        .toD("http:does-not-exist");

                from("direct:dynamic5")
                        .setHeader("CamelHttpUri", constant(getTestServerUrl() + "/hello"))
                        .toD("vertx-http:does-not-exist");

                from(getTestServerUri() + "/hello")
                        .setBody(constant("Hello World"));
            }
        };
    }
}
