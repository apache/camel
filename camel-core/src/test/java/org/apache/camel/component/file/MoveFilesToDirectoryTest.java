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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class MoveFilesToDirectoryTest extends ContextTestSupport {
    protected String testDirectory = "target/test/MoveFilesToDirectoryTest";
    protected String inputDirectory = testDirectory + "/input";
    protected String outputDirectory = testDirectory + "/output";
    protected String fileName = "foo.txt";
    protected Object expectedBody = "Hello there!";
    protected boolean noop;

    public void testFileRoute() throws Exception {
        template.sendBodyAndHeader("file:" + inputDirectory, expectedBody, FileComponent.HEADER_FILE_NAME, fileName);

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived(expectedBody);
        result.setResultWaitTime(5000);

        // now lets wait a bit and move that file
        Thread.sleep(5000);

        // lets delete the output directory
        deleteDirectory(outputDirectory);

        // now lets wait a bit for it to be polled
        Thread.sleep(5000);

        File file = new File(inputDirectory + "/" + fileName);

        File outDir = new File(outputDirectory);
        outDir.mkdirs();

        File newFile = new File(outDir, fileName);

        assertFileExists(file);
        assertFileNotExists(newFile);

        boolean answer = file.renameTo(newFile);
        assertTrue("Move of file: " + file + " to " + newFile + " should have worked!", answer);

        assertFileNotExists(file);
        assertFileExists(newFile);

        // now lets wait for multiple polls to check we only process it once
        Thread.sleep(5000);

        assertMockEndpointsSatisifed();
    }

    protected void assertFileNotExists(File file) {
        assertFalse("File should not exist: " + file, file.exists());
    }

    protected void assertFileExists(File file) {
        assertTrue("File should exist: " + file, file.exists());
    }

    @Override
    protected void setUp() throws Exception {
        deleteDirectory(testDirectory);
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getOutputEndpointUri()).to("mock:result");
            }
        };
    }

    protected String getOutputEndpointUri() {
        return "file:" + outputDirectory;
    }
}