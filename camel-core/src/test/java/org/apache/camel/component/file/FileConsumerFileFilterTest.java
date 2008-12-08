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
import java.io.FileFilter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for  the file filter option
 */
public class FileConsumerFileFilterTest extends ContextTestSupport {

    private String fileUrl = "file://target/filefilter/?filter=#myFilter";

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myFilter", new MyFileFilter());
        return jndi;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/filefilter");
    }

    public void testFilterFiles() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        template.sendBodyAndHeader("file:target/filefilter/", "This is a file to be filtered",
            FileComponent.HEADER_FILE_NAME, "skipme.txt");

        mock.setResultWaitTime(2000);
        mock.assertIsSatisfied();
    }

    public void testFilterFilesWithARegularFile() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file:target/filefilter/", "This is a file to be filtered",
            FileComponent.HEADER_FILE_NAME, "skipme.txt");

        template.sendBodyAndHeader("file:target/filefilter/", "Hello World",
            FileComponent.HEADER_FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).to("mock:result");
            }
        };
    }

    // START SNIPPET: e1
    public class MyFileFilter implements FileFilter {
        public boolean accept(File pathname) {
            // we dont accept any files starting with skip in the name
            return !pathname.getName().startsWith("skip");
        }
    }
    // END SNIPPET: e1

}