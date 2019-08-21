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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class OnExceptionHandledAndContinueTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testHandledAndContinued() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // should not be allowed
                onException(IllegalArgumentException.class).continued(true).handled(true);

                from("direct:start").to("mock:start").throwException(new IllegalArgumentException("Forced")).to("mock:result");
            }
        });
        try {
            context.start();
            fail("Should thrown an exception");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(e.getCause().getMessage().startsWith("Only one of handled or continued is allowed to be configured"));
        }
    }

}
