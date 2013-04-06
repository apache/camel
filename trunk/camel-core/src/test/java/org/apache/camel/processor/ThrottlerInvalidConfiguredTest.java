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
public class ThrottlerInvalidConfiguredTest extends ContextTestSupport {

    public void testInvalid() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // null is invalid
                from("seda:a").throttle(null).to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (FailedToCreateRouteException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("MaxRequestsPerPeriod expression must be provided"));
        }
        context.stop();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

}