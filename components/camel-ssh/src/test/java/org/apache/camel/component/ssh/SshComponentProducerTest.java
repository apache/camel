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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import org.junit.Test;

public class SshComponentProducerTest extends SshComponentTestSupport {

    @Test
    public void testProducer() throws Exception {
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
    public void testReconnect() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();

        sshd.stop();
        sshd.start();

        mock.reset();
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConnectionTimeout() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(0);

        MockEndpoint mockError = getMockEndpoint("mock:error");
        mockError.expectedMinimumMessageCount(1);

        sshd.stop();
        sshd = null;

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testCredentialsAsHeaders() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(SshResult.EXIT_VALUE, 0);
        mock.expectedHeaderReceived(SshResult.STDERR, "Error:test");
        
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(SshConstants.USERNAME_HEADER, "smx");
        headers.put(SshConstants.PASSWORD_HEADER, "smx");

        template.sendBodyAndHeaders("direct:sshCredentialsWithHeaders", msg, headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .handled(true)
                        .to("mock:error");

                from("direct:ssh")
                        .to("ssh://smx:smx@localhost:" + port + "?timeout=3000")
                        .to("mock:password");
                
                from("direct:sshCredentialsWithHeaders")
                        .to("ssh://localhost:" + port + "?timeout=3000")
                        .to("mock:password");
            }
        };
    }
}
