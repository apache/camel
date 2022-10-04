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
package org.apache.camel.itest.ftp;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.utils.extensions.FtpServiceExtension;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class FtpAndHttpRecipientListInterceptSendToEndpointIssueTest extends CamelTestSupport {
    @RegisterExtension
    public static FtpServiceExtension ftpServiceExtension = new FtpServiceExtension();

    protected static int httpPort;

    @BeforeAll
    public static void initPort() throws Exception {
        httpPort = AvailablePortFinder.getNextAvailable();
    }

    @Test
    void testFtpAndHttpIssue() throws Exception {
        String ftp = ftpServiceExtension.getAddress();
        String http = "http://localhost:" + httpPort + "/myapp";

        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:intercept").expectedMessageCount(3);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "seda:foo," + ftp + "," + http);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("(ftp|http|seda):.*")
                        .to("mock:intercept");

                from("direct:start")
                        .recipientList(header("foo"))
                        .to("mock:result");

                from("jetty:http://0.0.0.0:" + httpPort + "/myapp")
                        .transform().constant("Bye World");

                from("seda:foo").to("mock:foo");
            }
        };
    }
}
