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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class FileRecursiveNoopTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/noop");
        super.setUp();
    }

    public void testRecursiveNoop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a", "b", "a2", "c", "b2");

        template.sendBodyAndHeader("file:target/noop", "a", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/noop", "b", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file:target/noop/foo", "a2", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/noop/bar", "c", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader("file:target/noop/bar", "b2", Exchange.FILE_NAME, "b.txt");

        assertMockEndpointsSatisfied();

        // reset mock and send in a new file to be picked up only
        mock.reset();
        mock.expectedBodiesReceived("c2");

        template.sendBodyAndHeader("file:target/noop", "c2", Exchange.FILE_NAME, "c.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/noop?initialDelay=0&delay=10&recursive=true&noop=true")
                    .convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }
}
