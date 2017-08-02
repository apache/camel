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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test that file consumer will skip any files starting with a dot
 */
public class FileConsumerSkipDotFilesTest extends ContextTestSupport {

    private String fileUrl = "file://target/dotfiles/?initialDelay=0&delay=10";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/dotfiles");
        super.setUp();
    }

    public void testSkipDotFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);
        mock.setResultWaitTime(100);

        template.sendBodyAndHeader("file:target/dotfiles/", "This is a dot file",
            Exchange.FILE_NAME, ".skipme");

        mock.assertIsSatisfied();
    }

    public void testSkipDotFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/dotfiles/", "This is a dot file",
            Exchange.FILE_NAME, ".skipme");

        template.sendBodyAndHeader("file:target/dotfiles/", "Hello World",
            Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
