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

public class ConsumerAutoReconnectAfterIdleTimeoutTest extends SoroushBotTestSupport {

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("soroush://" + SoroushAction.getMessage + "/7 delay 100?reconnectIdleConnectionTimeout=200&maxConnectionRetry=0")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void test() throws InterruptedException {
        //checking is take place in createRouteBuilder
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        // at least 7*3 + x message should received since we know are not sure about x we expect it to be 1
        // the pattern is 7 message in 700ms and then at most 400ms wait so it must be at least 22 if it reaches 4th group of message
        // manually reconnect should not effect connection retry count
        mockEndpoint.setMinimumExpectedMessageCount(22);
        mockEndpoint.setResultWaitTime(4000);
        mockEndpoint.assertIsSatisfied();

    }
}
