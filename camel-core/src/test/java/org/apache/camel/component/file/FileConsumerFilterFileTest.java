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
 * Unit test for  the filter file option
 */
public class FileConsumerFilterFileTest extends ContextTestSupport {

    private String fileUrl = "file://target/filefilter/?initialDelay=0&delay=10&filterFile=${bodyAs(String)} contains 'World'";
    private String fileUrl2 = "file://target/filefilter/?initialDelay=0&delay=10&filterFile=${file:modified} < ${date:now-2s}";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/filefilter");
        super.setUp();
    }

    public void testFilterFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("file:target/filefilter/", "This is a file to be filtered", Exchange.FILE_NAME, "skipme.txt");

        mock.setResultWaitTime(100);
        mock.assertIsSatisfied();
    }

    public void testFilterFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/filefilter/", "This is a file to be filtered", Exchange.FILE_NAME, "skipme2.txt");
        template.sendBodyAndHeader("file:target/filefilter/", "Hello World", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
    }

    public void testFilterFilesWithDate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result2");
        mock.expectedBodiesReceived("Something else");

        template.sendBodyAndHeader("file:target/filefilter/", "Something else", Exchange.FILE_NAME, "hello2.txt");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).convertBodyTo(String.class).to("mock:result");
                from(fileUrl2).convertBodyTo(String.class).to("mock:result2");
            }
        };
    }

}