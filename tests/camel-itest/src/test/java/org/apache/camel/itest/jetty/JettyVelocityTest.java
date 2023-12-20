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
package org.apache.camel.itest.jetty;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class JettyVelocityTest extends CamelTestSupport {

    private int port;

    @Test
    void testClasspath() {
        Map<String, Object> map = new HashMap<>();
        map.put("firstName", "John");
        map.put("lastName", "Doe");

        String response
                = template.requestBodyAndHeaders("velocity:org/apache/camel/itest/jetty/header.vm", "", map, String.class);

        assertEquals("Dear Doe, John", response);
    }

    @Test
    void testClasspathInvalidParameter() {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("firstName", "John");
            map.put("lastName", "Doe");

            template.requestBodyAndHeaders("velocity:org/apache/camel/itest/jetty/?name=header.vm", "", map, String.class);
            fail("Should have thrown exception");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getMessage().endsWith("Unknown parameters=[{name=header.vm}]"));
        }
    }

    @Test
    void testHttp() {
        Map<String, Object> map = new HashMap<>();
        map.put("firstName", "John");
        map.put("lastName", "Doe");

        String response = template.requestBodyAndHeaders("velocity://http://localhost:" + port + "/test?name=header.vm", "",
                map, String.class);

        assertEquals("Dear Doe, John", response);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:" + port + "/test")
                        .process(exchange -> {
                            String name = exchange.getIn().getHeader("name", String.class);
                            ObjectHelper.notNull(name, "name");

                            name = "org/apache/camel/itest/jetty/" + name;
                            InputStream is
                                    = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), name);
                            String xml = exchange.getContext().getTypeConverter().convertTo(String.class, is);

                            exchange.getMessage().setBody(xml);
                            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                        });
            }
        };
    }

}
