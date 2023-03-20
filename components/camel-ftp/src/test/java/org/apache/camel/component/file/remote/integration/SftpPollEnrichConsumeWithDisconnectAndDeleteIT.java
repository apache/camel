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
package org.apache.camel.component.file.remote.integration;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.sftp.integration.SftpServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class SftpPollEnrichConsumeWithDisconnectAndDeleteIT extends SftpServerTestSupport {

    @Test
    void testSftpSimpleConsume() throws Exception {
        String expected = "Hello World";

        // create file using regular file
        template.sendBodyAndHeader("file://{{ftp.root.dir}}", expected, Exchange.FILE_NAME, "hello.txt");

        File file = ftpFile("hello.txt").toFile();
        await().atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() -> assertTrue(file.exists(), "The file should have been created"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        ProducerTemplate triggerTemplate = context.createProducerTemplate();
        triggerTemplate.sendBody("seda:trigger", "");

        mock.setResultWaitTime(TimeUnit.MINUTES.toMillis(3));
        mock.assertIsSatisfied();

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(file.exists(), "The file should have been deleted"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:trigger")
                        .pollEnrich("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                                    + "?username=admin&password=admin&delay=10000&disconnect=true&delete=true")
                        .routeId("foo").to("mock:result");
            }
        };
    }
}
