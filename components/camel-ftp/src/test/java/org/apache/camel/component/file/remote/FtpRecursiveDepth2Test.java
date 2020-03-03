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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class FtpRecursiveDepth2Test extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/depth2?password=admin&recursive=true";
    }

    @Test
    public void testDepthMin2Max99() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a2", "b2", "a3", "b3");

        template.sendBodyAndHeader("ftp://admin@localhost:" + getPort() + "/depth2?password=admin", "a", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader("ftp://admin@localhost:" + getPort() + "/depth2?password=admin", "b", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader("ftp://admin@localhost:" + getPort() + "/depth2/bar?password=admin", "b2", Exchange.FILE_NAME, "b2.txt");
        template.sendBodyAndHeader("ftp://admin@localhost:" + getPort() + "/depth2/foo?password=admin", "a2", Exchange.FILE_NAME, "a2.txt");
        template.sendBodyAndHeader("ftp://admin@localhost:" + getPort() + "/depth2/foo/bar?password=admin", "a3", Exchange.FILE_NAME, "a3.txt");
        template.sendBodyAndHeader("ftp://admin@localhost:" + getPort() + "/depth2/bar/foo?password=admin", "b3", Exchange.FILE_NAME, "b3.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl() + "&minDepth=2&maxDepth=99").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}
