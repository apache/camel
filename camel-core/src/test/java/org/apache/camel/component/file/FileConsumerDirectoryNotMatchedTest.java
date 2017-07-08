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
 * Unit test that file consumer will not match directories (CAMEL-920)
 */
public class FileConsumerDirectoryNotMatchedTest extends ContextTestSupport {

    private String fileUrl = "file://target/dirnotmatched/?initialDelay=0&delay=10&recursive=true&include=.*txt$";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/dirnotmatched");
        super.setUp();

        template.sendBodyAndHeader("file:target/dirnotmatched/", "This is a dot file",
            Exchange.FILE_NAME, ".skipme");

        template.sendBodyAndHeader("file:target/dirnotmatched/", "This is a web file",
            Exchange.FILE_NAME, "index.html");

        template.sendBodyAndHeader("file:target/dirnotmatched/2007", "2007 report",
            Exchange.FILE_NAME, "report2007.txt");

        template.sendBodyAndHeader("file:target/dirnotmatched/2008", "2008 report",
            Exchange.FILE_NAME, "report2008.txt");
    }

    public void testSkipDirectories() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

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
