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

import java.nio.file.Paths;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.junit.Test;

public class SshComponentSecurityTest extends SshComponentTestSupport {

    @Test
    public void testRsa() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:rsa");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh-rsa", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRsaFile() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:rsaFile");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh-rsaFile", msg);

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

                SshComponent sshComponent = new SshComponent();
                sshComponent.setHost("localhost");
                sshComponent.setPort(port);
                sshComponent.setUsername("smx");
                sshComponent.setKeyPairProvider(new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem")));
                sshComponent.setKeyType(KeyPairProvider.SSH_RSA);

                getContext().addComponent("ssh-rsa", sshComponent);

                from("direct:ssh-rsa")
                        .to("ssh-rsa:test")
                        .to("mock:rsa");

                from("direct:ssh-rsaFile")
                        .to("ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/hostkey.pem")
                        .to("mock:rsaFile");
            }
        };
    }
}
