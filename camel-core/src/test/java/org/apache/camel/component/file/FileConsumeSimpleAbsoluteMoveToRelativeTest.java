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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class FileConsumeSimpleAbsoluteMoveToRelativeTest extends ContextTestSupport {

    private String fileUrl = "file://target/move";
    private String base;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/move");
        // use current dir as base as absolute path
        base = new File("").getAbsolutePath() + "/target/move";
        super.setUp();
    }

    @Test
    public void testMoveToSubDir() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        mock.expectedFileExists(base + "/.done/bye.txt");
        mock.expectedFileExists(base + "/sub/.done/hello.txt");
        mock.expectedFileExists(base + "/sub/sub2/.done/goodday.txt");

        template.sendBodyAndHeader(fileUrl, "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader(fileUrl, "Hello World", Exchange.FILE_NAME, "sub/hello.txt");
        template.sendBodyAndHeader(fileUrl, "Goodday World", Exchange.FILE_NAME, "sub/sub2/goodday.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://" + base + "?recursive=true&move=.done&initialDelay=0&delay=10")
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}