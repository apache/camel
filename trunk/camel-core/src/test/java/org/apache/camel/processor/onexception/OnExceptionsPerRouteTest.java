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
package org.apache.camel.processor.onexception;

import java.io.IOException;
import java.net.ConnectException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class OnExceptionsPerRouteTest extends ContextTestSupport {

    public void testOnExceptionsPerRouteDamn() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("Damn");

        template.sendBody("direct:start", "Damn");

        assertMockEndpointsSatisfied();
    }

    public void testOnExceptionsPerRouteConnect() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("Connect");

        template.sendBody("direct:start", "Connect");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            @SuppressWarnings("unchecked")
            public void configure() throws Exception {
                from("direct:start")
                    .onException(IllegalArgumentException.class, IOException.class)
                        .handled(true)
                        .to("mock:error")
                    .end()
                    .choice()
                        .when(body().contains("Damn")).throwException(new IllegalArgumentException("Damn"))
                        .when(body().contains("Connect")).throwException(new ConnectException("Cannot connect"))
                    .end();
            }
        };
    }
}
