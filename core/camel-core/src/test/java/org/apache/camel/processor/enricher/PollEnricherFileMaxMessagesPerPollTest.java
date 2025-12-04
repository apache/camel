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

package org.apache.camel.processor.enricher;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

public class PollEnricherFileMaxMessagesPerPollTest extends ContextTestSupport {

    @Test
    public void testPollEnrichMaxMessagesPerPollInvalid() throws Exception {
        getMockEndpoint("mock:dead")
                .expectedBodiesReceived(
                        "The option maxMessagesPerPoll is not supported for polling consumer (such as when using poll or pollEnrich EIP)");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class)
                        .handled(true)
                        .transform(simple("${exception.getCause().getMessage()}"))
                        .to("mock:dead");

                from("timer:hello?repeatCount=1&delay=10")
                        .pollEnrich("file:target/temp?maxMessagesPerPoll=3&noop=true&fileName=doesnotexist.csv", 1000);
            }
        };
    }
}
