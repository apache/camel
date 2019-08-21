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
package org.apache.camel.component.file;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class FileRecursiveDepthTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/depth");
        super.setUp();
    }

    @Test
    public void testDepth() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a2", "b2");

        template.sendBodyAndHeader("file:target/data/depth", "a", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/depth", "b", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file:target/data/depth/foo", "a2", Exchange.FILE_NAME, "a2.txt");
        template.sendBodyAndHeader("file:target/data/depth/foo/bar", "a3", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/depth/bar", "b2", Exchange.FILE_NAME, "b2.txt");
        template.sendBodyAndHeader("file:target/data/depth/bar/foo", "b3", Exchange.FILE_NAME, "b.txt");

        // only expect 2 of the 6 sent, those at depth 2
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDepthMin2Max99() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedBodiesReceivedInAnyOrder("a2", "b2", "a3", "b3");

        template.sendBodyAndHeader("file:target/data/depth2", "a", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/depth2", "b", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file:target/data/depth2/bar", "b2", Exchange.FILE_NAME, "b2.txt");
        template.sendBodyAndHeader("file:target/data/depth2/foo", "a2", Exchange.FILE_NAME, "a2.txt");
        template.sendBodyAndHeader("file:target/data/depth2/foo/bar", "a3", Exchange.FILE_NAME, "a3.txt");
        template.sendBodyAndHeader("file:target/data/depth2/bar/foo", "b3", Exchange.FILE_NAME, "b3.txt");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMin1Max1() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedBodiesReceivedInAnyOrder("a", "b");

        template.sendBodyAndHeader("file:target/data/depth3", "a", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/depth3", "b", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file:target/data/depth3/foo", "a2", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/depth3/foo/bar", "a3", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("file:target/data/depth3/bar", "b2", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("file:target/data/depth3/bar/foo", "b3", Exchange.FILE_NAME, "b.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("file:target/data/depth?initialDelay=0&delay=10&recursive=true&minDepth=2&maxDepth=2").convertBodyTo(String.class).to("mock:result");

                from("file:target/data/depth2?initialDelay=0&delay=10&recursive=true&minDepth=2&maxDepth=99").convertBodyTo(String.class).to("mock:result");

                from("file:target/data/depth3?initialDelay=0&delay=10&recursive=true&minDepth=1&maxDepth=1").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
