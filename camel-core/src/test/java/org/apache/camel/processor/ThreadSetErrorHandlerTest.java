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
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test that verifies that thread does not allow add a error handler
 * as it will not kick in as expected by end-users.
 */
public class ThreadSetErrorHandlerTest extends ContextTestSupport {

    public void testNotAllowed() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                public void configure() throws Exception {
                    from("direct:start")
                        .thread(2)
                        // add error handler on thread is not allowed instead set on the parent (from)
                        .errorHandler(deadLetterChannel("mock:error"))
                        .to("mock:end");
                }
            });
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

}