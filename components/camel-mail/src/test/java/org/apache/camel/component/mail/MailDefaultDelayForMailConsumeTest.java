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
package org.apache.camel.component.mail;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for testing mail polling is happening according to the default poll interval.
 */
public class MailDefaultDelayForMailConsumeTest extends ContextTestSupport {

    public void testConsuming() throws Exception {
        template.sendBody("smtp://bond@localhost", "Hello London");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello London");
        // first poll should happend immediately
        mock.setResultWaitTime(2000L);
        mock.assertIsSatisfied();

        long start = System.currentTimeMillis();
        mock.reset();
        template.sendBody("smtp://bond@localhost", "Hello Paris");
        mock.expectedBodiesReceived("Hello Paris");
        // poll next mail and that is should be done within the default delay (overrule to 5 sec) + 2 sec slack
        mock.setResultWaitTime(5000L + 2000L);
        mock.assertIsSatisfied();
        long delta = System.currentTimeMillis() - start;
        assertTrue("Camel should not default poll the mailbox to often", delta > 5000 - 1000L);
    }


    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // we overrule the default of 60 sec to 5 so the unit test is faster
                from("pop3://bond@localhost?delay=5000").to("mock:result");
            }
        };
    }
}
