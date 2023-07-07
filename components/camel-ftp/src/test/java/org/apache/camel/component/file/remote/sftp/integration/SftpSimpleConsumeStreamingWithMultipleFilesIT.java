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

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpSimpleConsumeStreamingWithMultipleFilesIT extends SftpServerTestSupport {

    @Test
    public void testSftpSimpleConsume() throws Exception {
        String expected = "Hello World";
        String expected2 = "Goodbye World";

        // create file using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected2, Exchange.FILE_NAME, "goodbye.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceivedInAnyOrder(expected, expected2);

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);

        InputStream is = mock.getExchanges().get(0).getIn().getBody(InputStream.class);
        assertNotNull(is);
        InputStream is2 = mock.getExchanges().get(1).getIn().getBody(InputStream.class);
        assertNotNull(is2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&password=admin&delay=10000&disconnect=true&streamDownload=true&knownHostsFile="
                     + service.getKnownHostsFile()).routeId("foo")
                        .noAutoStartup().to("mock:result");
            }
        };
    }
}
