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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class WireTapNewExchangeTest extends ContextTestSupport {

    public void testFireAndForgetUsingExpressions() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");

        MockEndpoint tap = getMockEndpoint("mock:tap");
        tap.expectedBodiesReceived("Bye World");
        tap.expectedHeaderReceived("id", 123);
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        tap.expectedHeaderReceived("date", today);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // tap a new message and send it to direct:tap
                    // the new message should be Bye World with 2 headers
                    .wireTap("direct:tap")
                        // create the new tap message body and headers
                        .newExchangeBody(constant("Bye World"))
                        .newExchangeHeader("id", constant(123))
                        .newExchangeHeader("date", simple("${date:now:yyyyMMdd}"))
                    .end()
                    // here we continue routing the original messages
                    .to("mock:result");

                // this is the tapped route
                from("direct:tap")
                    .to("mock:tap");
                // END SNIPPET: e1
            }
        };
    }
}
