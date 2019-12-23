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
package org.apache.camel.component.mina;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class MinaSendToProcessorTest extends BaseMinaTest {

    @Test
    public void testConnectionOnStartupTest() throws Exception {
        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to(String.format("mina:tcp://localhost:%1$s?sync=false&lazySessionCreation=false", getPort()));
            }
        });

        try {
            context.start();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testConnectionOnSendMessage() throws Exception {
        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to(String.format("mina:tcp://localhost:%1$s?sync=false", getPort()));
            }
        });

        try {
            context.start();
        } catch (Exception e) {
            fail("Should not have thrown an exception");
        }

    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
