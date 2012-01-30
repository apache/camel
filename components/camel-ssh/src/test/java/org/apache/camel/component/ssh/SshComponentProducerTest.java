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
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.junit.Test;

public class SshComponentProducerTest extends SshComponentTestSupport {

    @Test
    public void testProducer() throws Exception {
        final String msg = "test\n";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testReconnect() throws Exception {
        final String msg = "test\n";

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
    public void testRsa() throws Exception {
        final String msg = "test\n";

        MockEndpoint mock = getMockEndpoint("mock:rsa");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh-rsa", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testConnectionTimeout() throws Exception {
        final String msg = "test\n";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(0);

        MockEndpoint mockError = getMockEndpoint("mock:error");
        mockError.expectedMinimumMessageCount(1);

        sshd.stop();
        sshd = null;

        template.sendBody("direct:ssh", msg);

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

                SshComponent sshComponent = new SshComponent();
                sshComponent.setHost("localhost");
                sshComponent.setPort(port);
                sshComponent.setUsername("smx");
                sshComponent.setKeyPairProvider(new FileKeyPairProvider(new String[]{"src/test/resources/hostkey.pem"}));
                sshComponent.setKeyType(KeyPairProvider.SSH_RSA);

                getContext().addComponent("ssh-rsa", sshComponent);

                from("direct:ssh-rsa")
                        .to("ssh-rsa:test")
                        .to("mock:rsa");
            }
        };
    }
}
