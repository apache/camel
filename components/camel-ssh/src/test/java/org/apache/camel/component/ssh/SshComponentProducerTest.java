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

import java.util.concurrent.CountDownLatch;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.Command;
import org.junit.Test;

public class SshComponentProducerTest extends CamelTestSupport {
    private SshServer sshd;
    private int port;

    @Override
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable(22000);

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        sshd.setKeyPairProvider(new FileKeyPairProvider(new String[]{"src/test/resources/hostkey.pem"}));
        sshd.setCommandFactory(new TestEchoCommandFactory());
        sshd.setPasswordAuthenticator(new BogusPasswordAuthenticator());
        sshd.setPublickeyAuthenticator(new BogusPublickeyAuthenticator());
        sshd.start();

        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (sshd != null) {
            sshd.stop(true);
            Thread.sleep(50);
        }
    }

    @Test
    public void testProducer() throws Exception {
        final String msg = "test\n";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(new Object[]{msg});

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testReconnect() throws Exception {
        final String msg = "test\n";

        MockEndpoint mock = getMockEndpoint("mock:password");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(new Object[]{msg});

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();

        sshd.stop();
        sshd.start();

        mock.reset();
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(new Object[]{msg});

        template.sendBody("direct:ssh", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRsa() throws Exception {
        final String msg = "test\n";

        MockEndpoint mock = getMockEndpoint("mock:rsa");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(new Object[]{msg});

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

        Thread.sleep(4000);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .handled(true)
                        .to("mock:error")
                        .to("log:error?showAll=true");

                from("direct:ssh")
                        .to("ssh://smx:smx@localhost:" + port + "?timeout=3000")
                        .to("mock:password")
                        .to("log:password?showAll=true");

                SshComponent sshComponent = new SshComponent();
                sshComponent.setHost("localhost");
                sshComponent.setPort(port);
                sshComponent.setUsername("smx");
                sshComponent.setKeyPairProvider(new FileKeyPairProvider(new String[]{"src/test/resources/hostkey.pem"}));
                sshComponent.setKeyType(KeyPairProvider.SSH_RSA);

                getContext().addComponent("ssh-rsa", sshComponent);

                from("direct:ssh-rsa")
                        .to("ssh-rsa:test")
                        .to("mock:rsa")
                        .to("log:rsa?showAll=true");
            }
        };
    }

    public static class TestEchoCommandFactory extends EchoCommandFactory {
        @Override
        public Command createCommand(String command) {
            return new TestEchoCommand(command);
        }

        public static class TestEchoCommand extends EchoCommand {
            public static CountDownLatch latch = new CountDownLatch(1);

            public TestEchoCommand(String command) {
                super(command);
            }

            @Override
            public void destroy() {
                if (latch != null) {
                    latch.countDown();
                }
                super.destroy();
            }
        }
    }
}
