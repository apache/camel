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
package org.apache.camel.component.iggy;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*",
                          disabledReason = "Iggy 0.6.0+ requires io_uring which is not available on CI environments")
public class IggyComponentIT extends IggyTestBase {

    @Test
    public void testComponent() throws Exception {
        MockEndpoint mockEndpoint = contextExtension.getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello Iggy");

        contextExtension.getProducerTemplate().sendBody("direct:start", "Hello Iggy");

        mockEndpoint.assertIsSatisfied();
    }

    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("iggy:%s?username=%s&password=%s&streamName=%s&host=%s&port=%d",
                                TOPIC, iggyService.username(), iggyService.password(), STREAM,
                                iggyService.host(), iggyService.port());

                fromF("iggy:%s?username=%s&password=%s&streamName=%s&host=%s&port=%d&consumerGroupName=%s",
                        TOPIC, iggyService.username(), iggyService.password(), STREAM,
                        iggyService.host(), iggyService.port(), CONSUMER_GROUP)
                        .to("mock:result");
            }
        };
    }
}
