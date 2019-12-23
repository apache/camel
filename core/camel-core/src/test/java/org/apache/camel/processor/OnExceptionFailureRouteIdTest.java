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

import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test to test that route id of the failed route is available to the end
 * user.
 */
public class OnExceptionFailureRouteIdTest extends DeadLetterChannelFailureRouteIdTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).handled(true).to("direct:dead");

                from("direct:foo").routeId("foo").to("mock:foo").to("direct:bar").to("mock:result");

                from("direct:bar").routeId("bar").to("mock:bar").throwException(new IllegalArgumentException("Forced"));

                from("direct:dead").log("Failed at route ${exchangeProperty.CamelFailureRouteId}").to("mock:dead");
            }
        };
    }
}
