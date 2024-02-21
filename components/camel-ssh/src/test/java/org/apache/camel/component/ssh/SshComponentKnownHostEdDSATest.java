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
package org.apache.camel.component.ssh;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SshComponentKnownHostEdDSATest extends SshComponentTestSupport {

    @Test
    public void testProducerWithEdDSAKeyType() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(SshConstants.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshConstants.STDERR, "Error:test");

        template.sendBody("direct:ssh", msg);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getHostKey() {
        return "src/test/resources/key_ed25519.pem";
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");

                from("ssh://smx:smx@localhost:" + port
                     + "?useFixedDelay=true&delay=40000&pollCommand=test%0A&knownHostsResource=classpath:known_hosts_eddsa&failOnUnknownHost=true")
                        .to("mock:result");

                from("direct:ssh")
                        .to("ssh://smx:smx@localhost:" + port
                            + "?timeout=3000&knownHostsResource=classpath:known_hosts_eddsa&failOnUnknownHost=true")
                        .to("mock:password");
            }
        };
    }
}
