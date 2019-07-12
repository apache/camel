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
package org.apache.camel.component.soroushbot.component;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.soroushbot.models.SoroushAction;
import org.apache.camel.component.soroushbot.support.SoroushBotTestSupport;
import org.junit.Test;

public class ConsumerRetryOnConnectionFailureTest extends SoroushBotTestSupport {
    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("soroush://" + SoroushAction.getMessage + "/5 CLOSE?maxConnectionRetry=1")
                        .to("mock:ConsumerRetryOnConnectionFailure");
            }
        };
    }

    @Test
    public void checkIfConsumerReceive15message() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:ConsumerRetryOnConnectionFailure");
        mockEndpoint.expectedMessageCount(15);
        mockEndpoint.assertIsSatisfied();
    }
}
