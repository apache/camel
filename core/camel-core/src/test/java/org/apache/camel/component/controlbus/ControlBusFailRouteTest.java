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
package org.apache.camel.component.controlbus;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RouteError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ControlBusFailRouteTest extends ContextTestSupport {

    @Test
    public void testControlBusFail() throws Exception {
        assertEquals("Started", context.getRouteController().getRouteStatus("foo").name());

        template.sendBody("direct:foo", "Hello World");

        // runs async so it can take a little while
        await().atMost(5, TimeUnit.SECONDS).until(() -> context.getRouteController().getRouteStatus("foo").isStopped());

        Route route = context.getRoute("foo");
        RouteError re = route.getLastError();
        Assertions.assertNotNull(re);
        Assertions.assertTrue(re.isUnhealthy());
        Assertions.assertEquals(RouteError.Phase.STOP, re.getPhase());
        Throwable cause = re.getException();
        Assertions.assertNotNull(cause);
        Assertions.assertEquals("Forced by Donkey Kong", cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("controlbus:route?routeId=current&action=fail&async=true"));

                from("direct:foo").routeId("foo")
                        .throwException(new IllegalArgumentException("Forced by Donkey Kong"));
            }
        };
    }
}
