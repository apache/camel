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

import java.io.File;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Checks that body of type {@link java.io.File} is simply moved avoiding
 * copying using IO streams.
 */
public class FileProducerFileBodyGetsMovedTest extends ContextTestSupport {

    @Before
    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data/filemove");
        super.setUp();
    }

    @Test
    public void testStoreFileExchangeBodyIsFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedFileExists("target/data/filemove/testStoreFile");
        mock.expectedMessageCount(1);
        File temporaryFile = File.createTempFile("camel", "test");

        template.requestBodyAndHeader("direct:in", temporaryFile, Exchange.FILE_LOCAL_WORK_PATH, temporaryFile);

        mock.assertIsSatisfied();
        assertFalse("Temporary body file should have been moved, not copied", temporaryFile.exists());
    }

    @Test
    public void testStoreFileExchangeBodyIsWrappedFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedFileExists("target/data/filemove/testStoreFile");
        mock.expectedMessageCount(1);
        File temporaryFile = File.createTempFile("camel", "test");

        GenericFile<File> body = new GenericFile<>();
        body.setFile(temporaryFile);
        template.requestBodyAndHeader("direct:in", temporaryFile, Exchange.FILE_LOCAL_WORK_PATH, temporaryFile);

        mock.assertIsSatisfied();
        assertFalse("Temporary body file should have been moved, not copied", temporaryFile.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").to("file://target/data/filemove/?fileName=testStoreFile").to("mock:result");
            }
        };
    }

}
