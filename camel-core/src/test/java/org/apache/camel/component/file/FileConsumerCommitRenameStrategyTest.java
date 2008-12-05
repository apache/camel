/**
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

import java.io.File;
import java.io.FileWriter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;

/**
 * Unit test for the FileRenameStrategy using move options
 */
public class FileConsumerCommitRenameStrategyTest extends ContextTestSupport {

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        deleteDirectory("target/done");
        deleteDirectory("target/reports");
    }

    public void testRenameSuccess() throws Exception {
        deleteDirectory("target/done");
        deleteDirectory("target/reports");

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("file:target/reports", "Hello Paris", FileComponent.HEADER_FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();

        // sleep to let the file consumer do its renaming
        Thread.sleep(100);

        // content of file should be Hello Paris
        String content = IOConverter.toString(new File("./target/done/paris.txt"));
        assertEquals("The file should have been renamed", "Hello Paris", content);
    }

    public void testRenameFileExists() throws Exception {
        deleteDirectory("target/done");
        deleteDirectory("target/reports");

        // create a file in done to let there be a duplicate file
        File file = new File("target/done");
        file.mkdirs();
        FileWriter fw = new FileWriter("./target/done/london.txt");
        fw.write("I was there once in London");
        fw.flush();
        fw.close();

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("file:target/reports", "Hello London", FileComponent.HEADER_FILE_NAME, "london.txt");

        mock.assertIsSatisfied();

        // sleep to let the file consumer do its renaming
        Thread.sleep(100);

        // content of file should be Hello London
        String content = IOConverter.toString(new File("./target/done/london.txt"));
        assertEquals("The file should have been renamed replacing any existing files", "Hello London", content);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/reports?moveNamePrefix=../done/&consumer.delay=5000").to("mock:report");
            }
        };
    }
}
