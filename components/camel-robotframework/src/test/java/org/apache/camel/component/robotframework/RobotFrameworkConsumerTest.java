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
package org.apache.camel.component.robotframework;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RobotFrameworkConsumerTest extends CamelTestSupport {

    @Test
    public void testConsumerRunsTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertNull(exchange.getIn().getBody());
        assertEquals(0, exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE, Integer.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("robotframework:src/test/resources/org/apache/camel/component/robotframework/send_no_camel_exchnage_only_camel_configs.robot"
                     + "?xunitFile=target/out-consumer.xml&outputDirectory=target&repeatCount=1")
                        .to("mock:result");
            }
        };
    }
}
