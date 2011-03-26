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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class DeadLetterChannelBuilderWithInvalidDeadLetterUriTest extends ContextTestSupport {

    public void testInvalidUri() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    errorHandler(deadLetterChannel("xxx"));

                    from("direct:start").to("mock:foo");
                }
            });

            fail("Should have thrown an exception");
        } catch (NoSuchEndpointException e) {
            assertEquals("No endpoint could be found for: xxx, please check your classpath contains the needed Camel component jar.", e.getMessage());
        }
    }

    public void testInvalidOption() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    errorHandler(deadLetterChannel("direct:error?foo=bar"));

                    from("direct:start").to("mock:foo");
                }
            });

            fail("Should have thrown an exception");
        } catch (ResolveEndpointFailedException e) {
            assertTrue(e.getMessage().endsWith("Unknown parameters=[{foo=bar}]"));
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
