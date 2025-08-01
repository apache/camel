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
package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StartingRoutesErrorReportedTest extends ContextTestSupport {

    @Test
    public void testInvalidFrom() {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start?foo=bar").routeId("route1").to("mock:result");
                }
            });
            context.start();
        });

        FailedToCreateRouteException fe = assertIsInstanceOf(FailedToCreateRouteException.class, e);
        assertEquals("route1", fe.getRouteId());
    }

    @Test
    public void testInvalidTo() {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").routeId("route2").to("direct:result?foo=bar");
                }
            });
            context.start();
        });

        assertTrue(
                e.getMessage().startsWith("Failed to create route: route2 at: >>> To[direct:result?foo=bar] <<< in route:"));
    }

    @Test
    public void testMaskPassword() {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("stub:foo?password=secret&beer=yes").routeId("route2").to("direct:result?foo=bar");
                }
            });
            context.start();
        });

        assertTrue(
                e.getMessage().startsWith("Failed to create route: route2 at: >>> To[direct:result?foo=bar] <<< in route:"));
    }

    @Test
    public void testInvalidBean() {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").routeId("route3").to("mock:foo").bean("");
                }
            });
            context.start();
        }, "Should have thrown exception");

        assertTrue(e.getMessage().startsWith("Failed to create route: route3 at: >>> Bean[ref:] <<< in route:"));
    }

    @Test
    public void testUnavailableDataFormatOnClasspath() {
        Exception e = assertThrows(Exception.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").routeId("route3").unmarshal().jaxb().log("Will never get here");
                }
            });
            context.start();
        }, "Should have thrown exception");

        assertTrue(e.getMessage().contains(
                "Ensure that the data format is valid and the associated Camel component is present on the classpath"));
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
