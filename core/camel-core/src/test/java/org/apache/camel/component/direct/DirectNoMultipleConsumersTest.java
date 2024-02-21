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
package org.apache.camel.component.direct;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * MultipleConsumers option test.
 */
public class DirectNoMultipleConsumersTest extends TestSupport {

    @Test
    public void testNoMultipleConsumersTest() throws Exception {
        CamelContext container = new DefaultCamelContext(false);
        container.disableJMX();

        container.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").to("mock:result");

                from("direct:in").to("mock:result");
            }
        });

        Assertions.assertThrows(FailedToStartRouteException.class, () -> container.start(),
                "Should have thrown an FailedToStartRouteException");

        container.stop();
    }
}
