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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.LambdaRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainLambdaRouteBuilderTest {
    private static final LambdaRouteBuilder BUILDER = rb -> rb.from("direct:start").to("mock:results");

    @Test
    public void testBindLambdaRouteBuilder() throws Exception {
        Main main = new Main();
        main.bind("myBarRoute", BUILDER);
        main.start();

        doTest(main.camelContext);

        main.stop();
    }

    @Test
    public void testAddLambdaRouteBuilder() throws Exception {
        Main main = new Main();
        main.configure().addLambdaRouteBuilder(BUILDER);
        main.start();

        doTest(main.camelContext);

        main.stop();
    }

    private static void doTest(CamelContext camelContext) throws Exception {
        assertNotNull(camelContext);
        assertEquals(1, camelContext.getRoutes().size());

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("Hello World");

        camelContext.createProducerTemplate().sendBody("direct:start", "Hello World");

        endpoint.assertIsSatisfied();
    }
}
