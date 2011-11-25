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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class FromFtpRecursiveNoopTest extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/noop?password=admin&binary=false&initialDelay=3000"
                + "&recursive=true&noop=true";
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        template.sendBodyAndHeader(getFtpUrl(), "a", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(getFtpUrl(), "b", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(getFtpUrl(), "a2", Exchange.FILE_NAME, "foo/a.txt");
        template.sendBodyAndHeader(getFtpUrl(), "c", Exchange.FILE_NAME, "bar/c.txt");
        template.sendBodyAndHeader(getFtpUrl(), "b2", Exchange.FILE_NAME, "bar/b.txt");
    }

    @Test
    public void testRecursiveNoop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("a", "b", "a2", "c", "b2");

        assertMockEndpointsSatisfied();

        // reset mock and send in a new file to be picked up only
        mock.reset();
        mock.expectedBodiesReceived("c2");

        template.sendBodyAndHeader(getFtpUrl(), "c2", Exchange.FILE_NAME, "c.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getFtpUrl())
                    .convertBodyTo(String.class)
                    .to("log:ftp")
                    .to("mock:result");
            }
        };
    }
}
