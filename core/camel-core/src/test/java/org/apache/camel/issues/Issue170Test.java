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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class Issue170Test extends ContextTestSupport {
    protected String qOne = "seda:Q1";
    protected String qTwo = "mock:Q2";
    protected String qThree = "mock:Q3";

    @Test
    public void testSendMessagesGetCorrectCounts() throws Exception {
        MockEndpoint q2 = getMockEndpoint(qTwo);
        MockEndpoint q3 = getMockEndpoint(qThree);

        String body1 = "<message id='1'/>";
        String body2 = "<message id='2'/>";

        q2.expectedBodiesReceived(body1, body2);
        q3.expectedBodiesReceived(body1, body2);

        template.sendBodyAndHeader("direct:start", body1, "counter", 1);
        template.sendBodyAndHeader("direct:start", body2, "counter", 2);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to(qOne);
                from(qOne).to(qTwo, qThree); // write to Q3 but not to Q2
            }
        };
    }
}
