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

    @Test
    public void testRsaFilePKCS8() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:rsapkcs8");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh-rsapkcs8", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEncryptedRsaFile() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:encrsaFile");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh-encrsaFile", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testECFile() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:ecFile");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh-ecFile", msg);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testECFilePKCS8() throws Exception {
        final String msg = "test";

        MockEndpoint mock = getMockEndpoint("mock:ecFilepkcs8");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);

        template.sendBody("direct:ssh-ecFilepkcs8", msg);

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
                sshComponent.getConfiguration().setHost("localhost");
                sshComponent.getConfiguration().setPort(port);
                sshComponent.getConfiguration().setUsername("smx");
                sshComponent.getConfiguration().setKeyPairProvider(new FileKeyPairProvider(Paths.get("src/test/resources/hostkey.pem")));
                sshComponent.getConfiguration().setKeyType(KeyPairProvider.SSH_RSA);

                getContext().addComponent("ssh-rsa", sshComponent);

                from("direct:ssh-rsa")
                        .to("ssh-rsa:test")
                        .to("mock:rsa");

                from("direct:ssh-rsaFile")
                        .to("ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/hostkey.pem")
                        .to("mock:rsaFile");

                from("direct:ssh-rsapkcs8")
                        .to("ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/rsa.pem")
                        .to("mock:rsapkcs8");

                from("direct:ssh-encrsaFile")
                        .to("ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/encrsa.pem&certResourcePassword=security")
                        .to("mock:encrsaFile");

                from("direct:ssh-ecFile")
                        .to("ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/ec.pem")
                        .to("mock:ecFile");


                from("direct:ssh-ecFilepkcs8")
                        .to("ssh://smx@localhost:" + port + "?certResource=file:src/test/resources/ecpkcs8.pem")
                        .to("mock:ecFilepkcs8");
            }
        };
    }
}
