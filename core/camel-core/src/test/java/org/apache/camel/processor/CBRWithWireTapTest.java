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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class CBRWithWireTapTest extends ContextTestSupport {

    @Test
    public void testCBRWithWireTapCamel() throws Exception {
        getMockEndpoint("mock:other").expectedMessageCount(0);
        getMockEndpoint("mock:camel").expectedMessageCount(1);
        getMockEndpoint("mock:donkey").expectedMessageCount(0);

        template.sendBody("direct:start", "Camel rules");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCBRWithWireTapDonkey() throws Exception {
        getMockEndpoint("mock:other").expectedMessageCount(0);
        getMockEndpoint("mock:camel").expectedMessageCount(0);
        getMockEndpoint("mock:donkey").expectedMessageCount(1);

        template.sendBody("direct:start", "Donkey kong");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCBRWithWireTapOther() throws Exception {
        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:camel").expectedMessageCount(0);
        getMockEndpoint("mock:donkey").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").choice().when(body().contains("Camel")).wireTap("mock:camel").end().when(body().contains("Donkey")).wireTap("mock:donkey").end().otherwise()
                    .to("mock:other");
            }
        };
    }
}
