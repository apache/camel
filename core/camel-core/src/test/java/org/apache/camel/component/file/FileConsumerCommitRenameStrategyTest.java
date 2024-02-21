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
package org.apache.camel.component.file;

import java.io.FileWriter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for the FileRenameStrategy using move options
 */
public class FileConsumerCommitRenameStrategyTest extends ContextTestSupport {

    @Test
    public void testRenameSuccess() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Paris");
        mock.expectedFileExists(testFile("done/paris.txt"), "Hello Paris");

        template.sendBodyAndHeader(fileUri("reports"), "Hello Paris", Exchange.FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();
    }

    @Test
    public void testRenameFileExists() throws Exception {
        // create a file in done to let there be a duplicate file
        testDirectory("done", true);

        try (FileWriter fw = new FileWriter(testFile("done/london.txt").toFile())) {
            fw.write("I was there once in London");
            fw.flush();
        }

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader(fileUri("reports"), "Hello London", Exchange.FILE_NAME, "london.txt");

        mock.assertIsSatisfied();

        oneExchangeDone.matchesWaitTime();

        // content of file should be Hello London
        String content = IOConverter.toString(testFile("done/london.txt").toFile(), null);
        assertEquals("Hello London", content, "The file should have been renamed replacing any existing files");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri("reports?move=../done/${file:name}&initialDelay=0&delay=10")).convertBodyTo(String.class)
                        .to("mock:report");
            }
        };
    }
}
