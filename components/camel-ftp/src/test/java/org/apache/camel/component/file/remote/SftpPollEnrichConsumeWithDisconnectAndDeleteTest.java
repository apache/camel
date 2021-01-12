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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.sftp.SftpServerTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;

@EnabledIf(value = "org.apache.camel.component.file.remote.services.SftpEmbeddedService#hasRequiredAlgorithms")
public class SftpPollEnrichConsumeWithDisconnectAndDeleteTest extends SftpServerTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SftpPollEnrichConsumeWithDisconnectAndDeleteTest.class);

    @Timeout(value = 30)
    @Test
    public void testSftpSimpleConsume() throws Exception {
        String expected = "Hello World";

        // create file using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        ProducerTemplate triggerTemplate = context.createProducerTemplate();
        triggerTemplate.sendBody("vm:trigger", "");

        assertMockEndpointsSatisfied();

        long startFileDeletionCheckTime = System.currentTimeMillis();
        boolean fileExists = true;
        while (System.currentTimeMillis() - startFileDeletionCheckTime < 3000) { // wait
                                                                                // up
                                                                                // to
                                                                                // 3000ms
                                                                                // for
                                                                                // file
                                                                                // to
                                                                                // be
                                                                                // deleted
            File file = new File(service.getFtpRootDir() + "/hello.txt");
            fileExists = file.exists();

            if (fileExists) {
                LOG.info("Will check that file has been deleted again in 200ms");
                Thread.sleep(200);
            }
        }

        assertFalse(fileExists, "The file should have been deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("vm:trigger")
                        .pollEnrich("sftp://localhost:{{ftp.server.port}}/" + service.getFtpRootDir()
                                    + "?username=admin&password=admin&delay=10000&disconnect=true&delete=true")
                        .routeId("foo").to("mock:result");
            }
        };
    }
}
