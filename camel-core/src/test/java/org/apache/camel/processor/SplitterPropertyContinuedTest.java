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
 *
 */
public class SplitterPropertyContinuedTest extends ContextTestSupport {
    
    public void testSplitterPropertyContinued() throws Exception {
        getMockEndpoint("mock:end").expectedBodiesReceived("A,Kaboom,B,C");
        getMockEndpoint("mock:end").message(0).exchangeProperty("errorCode").isNull();
        getMockEndpoint("mock:error").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:error").message(0).exchangeProperty("errorCode").isEqualTo("ERR-1");
        getMockEndpoint("mock:split").expectedBodiesReceived("A", "B", "C");
        getMockEndpoint("mock:split").allMessages().exchangeProperty("errorCode").isNull();
        
        template.sendBody("direct:start", "A,Kaboom,B,C");
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                    .continued(true)
                    .setProperty("errorCode", constant("ERR-1"));

                from("direct:start")
                    .split(body())
                        .log("Step #1 - Body: ${body} with error code: ${property.errorCode}")
                        .choice()
                            .when(body().contains("Kaboom"))
                                .throwException(new IllegalArgumentException("Damn"))
                        .end()
                        .log("Step #2 - Body: ${body} with error code: ${property.errorCode}")
                        .choice()
                            .when(simple("${property.errorCode} != null"))
                                .to("mock:error")
                            .otherwise()
                                .to("mock:split")
                        .end()
                    .end()
                    .to("mock:end");
            }
        };
    }
}
