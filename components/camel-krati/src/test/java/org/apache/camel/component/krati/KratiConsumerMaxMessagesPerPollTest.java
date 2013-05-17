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
package org.apache.camel.component.krati;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class KratiConsumerMaxMessagesPerPollTest extends CamelTestSupport {

    @Test
    public void testPutAndConsume() throws InterruptedException {
        MockEndpoint endpoint = context.getEndpoint("mock:results", MockEndpoint.class);
        // batch-1
        endpoint.message(0).property(Exchange.BATCH_SIZE).isEqualTo(2);
        endpoint.message(0).property(Exchange.BATCH_INDEX).isEqualTo(0);
        endpoint.message(0).property(Exchange.BATCH_COMPLETE).isEqualTo(false);
        endpoint.message(1).property(Exchange.BATCH_SIZE).isEqualTo(2);
        endpoint.message(1).property(Exchange.BATCH_INDEX).isEqualTo(1);
        endpoint.message(1).property(Exchange.BATCH_COMPLETE).isEqualTo(true);

        // batch-2
        endpoint.message(2).property(Exchange.BATCH_SIZE).isEqualTo(1);
        endpoint.message(2).property(Exchange.BATCH_INDEX).isEqualTo(0);
        endpoint.message(2).property(Exchange.BATCH_COMPLETE).isEqualTo(true);

        endpoint.expectedMessageCount(3);

        template.sendBodyAndHeader("direct:put", "TEST1", KratiConstants.KEY, "1");
        template.sendBodyAndHeader("direct:put", "TEST2", KratiConstants.KEY, "2");
        template.sendBodyAndHeader("direct:put", "TEST3", KratiConstants.KEY, "3");

        endpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:put")
                        .to("krati:target/test/consumertest");

                from("krati:target/test/consumertest?maxMessagesPerPoll=2")
                        .to("mock:results");

            }
        };
    }
}
