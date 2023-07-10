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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that the existence of a outMessage in an exchange will not break the move-file post-processing
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpMoveWithOutMessageTest extends SftpServerTestSupport {

    @Timeout(value = 30)
    @Test
    public void testMoveFileForMultiplePollEnrich() {
        String expected = "Hello World";

        // create two files using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello1.txt");
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello2.txt");

        ProducerTemplate triggerTemplate = context.createProducerTemplate();
        triggerTemplate.sendBody("seda:trigger", "");

        File fileInArchive = ftpFile("archive/hello1.txt").toFile();
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(fileInArchive.exists(), "The file should exist in the archive folder"));

        File fileInArchive2 = ftpFile("archive/hello2.txt").toFile();
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(fileInArchive2.exists(), "The file should exist in the archive folder"));

        File originalFile = ftpFile("hello1.txt").toFile();
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(originalFile.exists(), "The file should have been moved"));

        File originalFile2 = ftpFile("hello2.txt").toFile();
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(originalFile2.exists(), "The file should have been moved"));
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        TestProcessor processor = new TestProcessor();
        return new RouteBuilder[] { new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:trigger")
                        .pollEnrich(
                                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&delay="
                                    + "10000&disconnect=true&move=archive&knownHostsFile="
                                    + service.getKnownHostsFile())
                        .pollEnrich(
                                "sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}?username=admin&password=admin&delay="
                                    + "10000&disconnect=true&move=archive&knownHostsFile="
                                    + service.getKnownHostsFile())
                        .process(processor);
            }
        } };
    }

    private static class TestProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            DefaultMessage msg = new DefaultMessage(exchange);
            msg.setBody(exchange.getIn().getBody());
            msg.setHeaders(exchange.getIn().getHeaders());
            exchange.setOut(msg); // uses OUT on purpose for testing
        }
    }
}
