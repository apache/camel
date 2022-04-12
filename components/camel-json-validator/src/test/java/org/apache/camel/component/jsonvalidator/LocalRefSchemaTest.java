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
package org.apache.camel.component.jsonvalidator;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class LocalRefSchemaTest extends CamelTestSupport {

    @EndpointInject("mock:end")
    protected MockEndpoint endpoint;

    @Test
    public void testValidMessage() throws Exception {
        endpoint.expectedMessageCount(1);

        template.sendBody("direct:start",
                "{ \"customer\": \"Donald \\\"Duck\\\" Dunn\", \"orderItems\": [{ \"product\": \"bass guitar\", \"quantity\": 1 }] }");

        MockEndpoint.assertIsSatisfied(endpoint);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("json-validator:org/apache/camel/component/jsonvalidator/Order.json")
                        .to("mock:end");
            }
        };
    }
}
