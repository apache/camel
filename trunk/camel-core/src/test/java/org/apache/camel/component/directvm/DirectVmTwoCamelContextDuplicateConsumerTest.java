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
package org.apache.camel.component.directvm;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;

/**
 *
 */
public class DirectVmTwoCamelContextDuplicateConsumerTest extends AbstractDirectVmTestSupport {

    public void testThirdClash() throws Exception {
        CamelContext third = new DefaultCamelContext();
        third.addRoutes(createRouteBuilderForThirdContext());
        try {
            third.start();
            fail("Should have thrown exception");
        } catch (IllegalStateException e) {
            assertEquals("A consumer Consumer[direct-vm://foo] already exists from CamelContext: camel-1. Multiple consumers not supported", e.getMessage());
        }

        // stop first camel context then
        context.stop();

        // and start the 3rd which should work now
        third.start();

        MockEndpoint mock = third.getEndpoint("mock:third", MockEndpoint.class);
        mock.expectedMessageCount(1);

        template2.sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();

        third.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:foo")
                    .transform(constant("Bye World"))
                    .log("Running on Camel ${camelId} on thread ${threadName} with message ${body}")
                    .to("mock:result");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilderForSecondContext() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .log("Running on Camel ${camelId} on thread ${threadName} with message ${body}")
                    .to("direct-vm:foo");
            }
        };
    }

    protected RouteBuilder createRouteBuilderForThirdContext() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:foo")
                    .transform(constant("Bye World"))
                    .log("Running on Camel ${camelId} on thread ${threadName} with message ${body}")
                    .to("mock:third");
            }
        };
    }

}
