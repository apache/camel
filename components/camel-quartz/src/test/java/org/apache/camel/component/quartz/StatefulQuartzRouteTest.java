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
package org.apache.camel.component.quartz;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class StatefulQuartzRouteTest extends BaseQuartzTest {
    protected MockEndpoint resultEndpoint;

    @Test
    public void testQuartz() throws Exception {
        resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(2);
        resultEndpoint.message(0).header("triggerName").isEqualTo("myTimerName");
        resultEndpoint.message(0).header("triggerGroup").isEqualTo("myGroup");

        // lets test the receive worked
        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            Message in = exchange.getIn();
            log.debug("Received: " + in + " with headers: " + in.getHeaders());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // START SNIPPET: example
                from("quartz://myGroup/myTimerName?trigger.repeatInterval=2&trigger.repeatCount=1&stateful=true").to("mock:result");
                // END SNIPPET: example
            }
        };
    }
}
