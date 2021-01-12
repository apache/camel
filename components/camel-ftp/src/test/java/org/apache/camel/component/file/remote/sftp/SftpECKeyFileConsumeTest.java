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
package org.apache.camel.component.file.remote.sftp;

import java.security.interfaces.ECPublicKey;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf(value = "org.apache.camel.component.file.remote.services.SftpEmbeddedService#hasRequiredAlgorithms")
public class SftpECKeyFileConsumeTest extends SftpServerTestSupport {

    @Test
    public void testSftpSimpleConsume() throws Exception {
        String expected = "Hello World";

        // create file using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
    }

    protected PublickeyAuthenticator getPublickeyAuthenticator() {
        return (username, key, session) -> key instanceof ECPublicKey;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sftp://localhost:{{ftp.server.port}}/" + service.getFtpRootDir() + "?username=admin&knownHostsFile="
                     + service.getKnownHostsFile()
                     + "&privateKeyFile=./src/test/resources/ec.pem&delay=10000&disconnect=true").routeId("foo").noAutoStartup()
                             .to("mock:result");
            }
        };
    }
}
