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
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.Command;
import org.junit.Test;

public class SshComponentConsumerTest extends CamelTestSupport {
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
    public void testPollingConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(new Object[]{"test\r"});

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ssh://smx:smx@localhost:" + port + "?useFixedDelay=true&delay=5000&pollCommand=test%0D")
                        .to("mock:result")
                        .to("log:foo?showAll=true");
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
