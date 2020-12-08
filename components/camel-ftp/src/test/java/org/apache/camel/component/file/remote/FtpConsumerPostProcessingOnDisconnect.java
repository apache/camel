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
import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class FtpConsumerPostProcessingOnDisconnect extends FtpServerTestSupport {
    private static final String SAMPLE_FILE_NAME_1 = String.format("sample-1-%s.txt",
            FtpConsumerPostProcessingOnDisconnect.class.getSimpleName());
    private static final String SAMPLE_FILE_NAME_2 = String.format("sample-2-%s.txt",
            FtpConsumerPostProcessingOnDisconnect.class.getSimpleName());
    private static final String SAMPLE_FILE_CHARSET = "iso-8859-1";
    private static final String SAMPLE_FILE_PAYLOAD = "abc";

    @Test
    public void testConsumeDelete() throws Exception {
        // prepare sample file to be consumed by FTP consumer
        createSampleFile(SAMPLE_FILE_NAME_1);

        // Prepare expectations
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(SAMPLE_FILE_PAYLOAD);

        context.getRouteController().startRoute("foo");

        // Check that expectations are satisfied
        assertMockEndpointsSatisfied();

        // File is deleted
        Thread.sleep(250);
        File deletedFile = new File(service.getFtpRootDir() + "/" + SAMPLE_FILE_NAME_1);
        assertFalse(deletedFile.exists(), "File should have been deleted: " + deletedFile);
    }

    @Test
    public void testConsumeMove() throws Exception {
        // moved file after its processed
        String movedFile = service.getFtpRootDir() + "/.camel/" + SAMPLE_FILE_NAME_2;

        // prepare sample file to be consumed by FTP consumer
        createSampleFile(SAMPLE_FILE_NAME_2);

        // Prepare expectations
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(SAMPLE_FILE_PAYLOAD);
        // use mock to assert that the file will be moved there eventually
        mock.expectedFileExists(movedFile);

        context.getRouteController().startRoute("bar");

        // Check that expectations are satisfied
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("ftp://admin@localhost:{{ftp.server.port}}?password=admin&delete=true").routeId("foo").noAutoStartup()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                service.disconnectAllSessions(); // disconnect all Sessions on FTP server
                            }
                        }).to("mock:result");
                from("ftp://admin@localhost:{{ftp.server.port}}?password=admin&noop=false&move=.camel").routeId("bar")
                        .noAutoStartup()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                service.disconnectAllSessions(); // disconnect all Sessions on FTP server
                            }
                        }).to("mock:result");
            }
        };
    }

    private void createSampleFile(String fileName) throws IOException {
        File file = new File(service.getFtpRootDir() + "/" + fileName);
        FileUtils.write(file, SAMPLE_FILE_PAYLOAD, SAMPLE_FILE_CHARSET);
    }

}
