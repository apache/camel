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
package org.apache.camel.component.ssh;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SshComponentKnownHostTest extends SshComponentTestSupport {

    @Test
    public void testProducerWithValidFile() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(SshResult.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshResult.STDERR, "Error:test");

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerWithInvalidFile() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMessageCount(0);

        template.sendBody("direct:sshInvalid", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testProducerWithInvalidFileWarnOnly() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(SshResult.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshResult.STDERR, "Error:test");

        template.sendBody("direct:sshInvalidWarnOnly", msg);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testPollingConsumerWithValidKnownHostFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("test");
        mock.expectedHeaderReceived(SshResult.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshResult.STDERR, "Error:test");
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testPollingConsumerWithInvalidKnownHostFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultInvalid");
        mock.expectedMessageCount(0);
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testPollingConsumerWithInvalidKnownHostFileWarnOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultInvalidWarnOnly");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("test");
        mock.expectedHeaderReceived(SshResult.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshResult.STDERR, "Error:test");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");

                from("ssh://smx:smx@localhost:" + port + "?useFixedDelay=true&delay=40000&pollCommand=test%0A&knownHostsResource=classpath:known_hosts_valid&failOnUnknownHost=true")
                    .to("mock:result");
                
                from("ssh://smx:smx@localhost:" + port + "?useFixedDelay=true&delay=40000&pollCommand=test%0A&knownHostsResource=classpath:known_hosts_invalid&failOnUnknownHost=true")
                    .to("mock:resultInvalid");

                from("ssh://smx:smx@localhost:" + port + "?useFixedDelay=true&delay=40000&pollCommand=test%0A&knownHostsResource=classpath:known_hosts_invalid")
                    .to("mock:resultInvalidWarnOnly");

                from("direct:ssh")
                        .to("ssh://smx:smx@localhost:" + port
                                + "?timeout=3000&knownHostsResource=classpath:known_hosts_valid&failOnUnknownHost=true")
                        .to("mock:password");

                from("direct:sshInvalid").to("ssh://smx:smx@localhost:" + port
                        + "?timeout=3000&knownHostsResource=classpath:known_hosts_invalid&failOnUnknownHost=true")
                        .to("mock:password");

                from("direct:sshInvalidWarnOnly").to("ssh://smx:smx@localhost:" + port
                        + "?timeout=3000&knownHostsResource=classpath:known_hosts_invalid").to("mock:password");
            }
        };
    }
}
