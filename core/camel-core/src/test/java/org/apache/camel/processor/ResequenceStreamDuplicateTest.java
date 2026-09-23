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
import org.junit.jupiter.api.Test;

/**
 * The stream resequencer keeps the first of the messages with the same sequence number, as documented.
 */
public class ResequenceStreamDuplicateTest extends ContextTestSupport {

    @Test
    public void testDuplicateKeepsFirst() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A1", "B");

        template.sendBodyAndHeader("direct:start", "B", "seqno", 2);
        template.sendBodyAndHeader("direct:start", "A1", "seqno", 1);
        template.sendBodyAndHeader("direct:start", "A2", "seqno", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").resequence(header("seqno")).stream().timeout(2000).to("mock:result");
            }
        };
    }
}
