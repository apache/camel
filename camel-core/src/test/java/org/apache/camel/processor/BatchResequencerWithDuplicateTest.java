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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class BatchResequencerWithDuplicateTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testBatchResequencerAllowDuplicate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").resequence(header("id")).allowDuplicates().to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("1A", "1B", "2C", "2D", "2E", "2F", "3G", "4H");

        template.sendBodyAndHeader("direct:start", "1A", "id", "1");
        template.sendBodyAndHeader("direct:start", "2C", "id", "2");
        template.sendBodyAndHeader("direct:start", "2D", "id", "2");
        template.sendBodyAndHeader("direct:start", "4H", "id", "4");
        template.sendBodyAndHeader("direct:start", "1B", "id", "1");
        template.sendBodyAndHeader("direct:start", "2E", "id", "2");
        template.sendBodyAndHeader("direct:start", "3G", "id", "3");
        template.sendBodyAndHeader("direct:start", "2F", "id", "2");

        assertMockEndpointsSatisfied();
    }

    public void testBatchResequencerNoDuplicate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").resequence(header("id")).to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("A", "C", "E", "F");

        template.sendBodyAndHeader("direct:start", "A", "id", "1");
        template.sendBodyAndHeader("direct:start", "C", "id", "2");
        template.sendBodyAndHeader("direct:start", "D", "id", "2");
        template.sendBodyAndHeader("direct:start", "F", "id", "4");
        template.sendBodyAndHeader("direct:start", "B", "id", "1");
        template.sendBodyAndHeader("direct:start", "E", "id", "3");

        assertMockEndpointsSatisfied();
    }

}