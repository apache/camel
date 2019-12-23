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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class BatchResequencerJMSPriorityTest extends ContextTestSupport {

    @Test
    public void testBatchResequencerJMSPriority() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("G", "A", "B", "E", "H", "C", "D", "F");

        template.sendBodyAndHeader("direct:start", "A", "JMSPriority", 6);
        template.sendBodyAndHeader("direct:start", "B", "JMSPriority", 6);
        template.sendBodyAndHeader("direct:start", "C", "JMSPriority", 4);
        template.sendBodyAndHeader("direct:start", "D", "JMSPriority", 4);
        template.sendBodyAndHeader("direct:start", "E", "JMSPriority", 6);
        template.sendBodyAndHeader("direct:start", "F", "JMSPriority", 4);
        template.sendBodyAndHeader("direct:start", "G", "JMSPriority", 8);
        template.sendBodyAndHeader("direct:start", "H", "JMSPriority", 6);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // sort by JMSPriority by allowing duplicates (message can
                    // have same JMSPriority)
                    // and use reverse ordering so 9 is first output (most
                    // important), and 0 is last
                    .resequence(header("JMSPriority")).allowDuplicates().reverse().to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
