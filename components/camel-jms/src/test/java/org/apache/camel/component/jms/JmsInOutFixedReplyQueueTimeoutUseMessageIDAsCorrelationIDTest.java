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
package org.apache.camel.component.jms;

import java.time.Duration;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;

public class JmsInOutFixedReplyQueueTimeoutUseMessageIDAsCorrelationIDTest extends JmsInOutFixedReplyQueueTimeoutTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:JmsInOutFixedReplyQueueTimeoutTest")
                        .routeId("route-1")
                        .to(ExchangePattern.InOut,
                                "activemq:queue:JmsInOutFixedReplyQueueTimeoutUseMessageIDAsCorrelationIDTest?replyTo=queue:JmsInOutFixedReplyQueueTimeoutUseMessageIDAsCorrelationIDTestReply&useMessageIDAsCorrelationID=true&requestTimeout=2000")
                        .to("mock:result");

                from("activemq:queue:JmsInOutFixedReplyQueueTimeoutUseMessageIDAsCorrelationIDTest")
                        .routeId("route-2")
                        .choice().when(body().isEqualTo("World"))
                        .log("Sleeping for 4 sec to force a timeout")
                        .delay(Duration.ofSeconds(4).toMillis()).endChoice().end()
                        .transform(body().prepend("Bye ")).to("log:reply");
            }
        };
    }

}
