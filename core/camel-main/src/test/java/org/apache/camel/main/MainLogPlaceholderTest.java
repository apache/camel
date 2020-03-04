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
package org.apache.camel.main;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

public class MainLogPlaceholderTest extends Assert {

    @Test
    public void testMain() throws Exception {
        Main main = new Main();
        main.addInitialProperty("camel.context.name", "test-ctx");
        main.addInitialProperty("message", "test");
        main.addInitialProperty("tap", "mock:tap");
        main.addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:tick?period=10")
                    .log("{{message}}")
                    .transform().constant("Hello {{message}}")
                    .wireTap("{{tap}}");
            }
        });
        main.start();

        MockEndpoint tap = main.getCamelContext().getEndpoint("mock:tap", MockEndpoint.class);
        tap.expectedMinimumMessageCount(3);
        tap.allMessages().body().isEqualTo("Hello test");

        assertEquals("test-ctx", main.getCamelContext().getName());

        tap.assertIsSatisfied();

        main.stop();
    }

}
