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
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class TryCatchMustHaveExceptionConfiguredTest extends ContextTestSupport {

    public void testTryCatchMustHaveExceptionConfigured() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {
                from("direct:a")
                    .doTry()
                        .to("mock:b")
                        .throwException(new IllegalArgumentException("Damn"))
                    .doCatch()
                        .to("mock:catch")
                    .end();
            }
        });

        try {
            context.start();
            fail("Should throw exception");
        } catch (FailedToCreateRouteException e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("At least one Exception must be configured to catch", e.getCause().getMessage());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}