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
package org.apache.camel.component.file.remote.mina.sftp;

import java.nio.file.Files;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25829: pollEnrich and toD with a dynamic fileName use one mina-sftp endpoint, as they do with sftp, and the
 * name of the file is evaluated for each message. Before, each name of a file resolved an endpoint of its own.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/sftp/hostkey.pem')")
public class MinaSftpDynamicFileNameIT extends SftpServerTestSupport {

    private String sftpUrl(String dir) {
        return "mina-sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}/" + dir
               + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile();
    }

    @Test
    public void testPollEnrichWithDynamicFileName() throws Exception {
        Files.createDirectories(ftpFile("poll"));
        Files.writeString(ftpFile("poll/hello.txt"), "Hello World");
        Files.writeString(ftpFile("poll/bye.txt"), "Bye World");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World", "Bye World");

        template.sendBodyAndHeader("direct:poll", "Foo", "target", "hello.txt");
        template.sendBodyAndHeader("direct:poll", "Bar", "target", "bye.txt");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, endpointsWithFileName("poll"), "There should only be 1 mina-sftp endpoint");
    }

    @Test
    public void testToDWithDynamicFileName() throws Exception {
        template.sendBodyAndHeader("direct:write", "Hello World", "target", "hello.txt");
        template.sendBodyAndHeader("direct:write", "Bye World", "target", "bye.txt");

        assertEquals("Hello World", Files.readString(ftpFile("write/hello.txt")));
        assertEquals("Bye World", Files.readString(ftpFile("write/bye.txt")));
        assertEquals(1, endpointsWithFileName("write"), "There should only be 1 mina-sftp endpoint");
    }

    private long endpointsWithFileName(String dir) {
        return context.getEndpoints().stream()
                .map(e -> e.getEndpointUri())
                .filter(uri -> uri.startsWith("mina-sftp:") && uri.contains("/" + dir + "?") && uri.contains("fileName="))
                .count();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // a dynamic uri, as pollEnrich has in YAML and XML (pollEnrich with a String uri is a static uri)
                from("direct:poll")
                        .pollEnrich().simple(sftpUrl("poll") + "&noop=true&fileName=${header.target}").timeout(5000)
                        .to("mock:result");

                from("direct:write")
                        .toD(sftpUrl("write") + "&fileName=${header.target}");
            }
        };
    }
}
