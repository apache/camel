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
package org.apache.camel.builder;

import java.io.IOException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RouteOnExceptionHandledTest extends ContextTestSupport {

    @Test
    public void testOnExceptionHandled() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:io").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedBodiesReceived("Error: World");

        Exchange out = fluentTemplate.withBody("World").to("direct:start").send();
        Assertions.assertFalse(out.isFailed()); // should be handled

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .onException(IOException.class)
                        .maximumRedeliveries(3)
                        .log("IO error happened")
                        .process(exchange -> {
                            exchange.getMessage().setBody("IO: " + exchange.getMessage().getBody());
                        })
                        .to("mock:io")
                        .end()
                        .onException(IllegalArgumentException.class).handled(true)
                        .log("An error happened")
                        .process(exchange -> {
                            exchange.getMessage().setBody("Error: " + exchange.getMessage().getBody());
                        })
                        .to("mock:error")
                        .end()
                        .throwException(new IllegalArgumentException("Forced"))
                        .to("mock:result");
            }
        };
    }
}
