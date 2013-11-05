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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;


public class SedaErrorTest extends ContextTestSupport {

    @Test
    public void testErrorHandle() throws InterruptedException {
        MockEndpoint mockDLC = getMockEndpoint("mock:dlc");
        mockDLC.expectedMessageCount(1);

        for (int i = 0; i < 3; i++) {
            template.send("direct:start", ExchangeBuilder.anExchange(context).withBody("msg" + i).build());
        }

        assertMockEndpointsSatisfied();
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlc"));
                from("direct:start").log("start: ${body}").to("seda:seda1?size=2&blockWhenFull=false").log("after: ${body}");
            }
        };

    }
}