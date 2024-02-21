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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class RouteMustHaveOutputOnExceptionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testValid() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").onException(Exception.class).redeliveryDelay(10).maximumRedeliveries(2)
                        .backOffMultiplier(1.5).handled(true).delay(1000)
                        .log("Halting for some time").to("mock:halt").end().end().to("mock:result");
            }
        });
        context.start();
    }

    @Test
    public void testInValid() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").onException(Exception.class).redeliveryDelay(10).maximumRedeliveries(2)
                        .backOffMultiplier(1.5).handled(true).delay(1000)
                        .log("Halting for some time").to("mock:halt")
                        // end missing
                        .end().to("mock:result");
            }
        });

        assertThrows(Exception.class, () -> context.start(),
                "Should have thrown an exception");
    }

}
