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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpKeyConsumeIT extends SftpServerTestSupport {

    @Test
    public void testSftpSimpleConsume() throws Exception {
        String expected = "Hello World";

        // create file using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir().toString(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);
    }

    private byte[] getBytesFromFile(String filename) throws IOException {
        return Files.readAllBytes(Paths.get(filename));
    }

    @BindToRegistry("privateKey")
    public byte[] addPrivateKey() throws Exception {

        return getBytesFromFile("./src/test/resources/id_rsa");
    }

    @BindToRegistry("knownHosts")
    public byte[] addKnownHosts() throws Exception {

        return getBytesFromFile("./src/test/resources/known_hosts");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&knownHosts=#knownHosts&privateKey=#privateKey&privateKeyPassphrase=secret&delay=10000&strictHostKeyChecking=yes&useUserKnownHostsFile=false&disconnect=true")
                        .routeId("foo").noAutoStartup().to("mock:result");
            }
        };
    }
}
