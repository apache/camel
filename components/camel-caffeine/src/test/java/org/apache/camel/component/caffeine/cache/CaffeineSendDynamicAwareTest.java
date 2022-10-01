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
package org.apache.camel.component.caffeine.cache;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CaffeineSendDynamicAwareTest extends CaffeineCacheTestSupport {

    @Test
    public void testSendDynamic() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("VALUE_1");

        template.sendBodyAndHeaders("direct:start", "Hello World",
                Map.of("action1", "PUT", "action2", "GET", "myKey", "foobar"));

        MockEndpoint.assertIsSatisfied(context);

        // there are 1 caffeine endpoints
        int count = (int) context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith("caffeine-cache")).count();
        Assertions.assertEquals(1, count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://start")
                        .setBody(constant("VALUE_1"))
                        .toD("caffeine-cache://mycache?action=${header.action1}&key=${header.myKey}")
                        .setBody(constant("VALUE_2"))
                        .toD("caffeine-cache://mycache?key=${header.myKey}&action=${header.action2}")
                        .to("mock:result");
            }
        };
    }
}
