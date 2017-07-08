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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for the FileRenameStrategy using preMove options
 */
public class FileConsumerBeginRenameStrategyTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/inprogress");
        deleteDirectory("target/reports");
        super.setUp();
    }

    public void testRenameSuccess() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("file:target/reports", "Hello Paris", Exchange.FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();
    }

    public void testRenameFileExists() throws Exception {
        // create a file in inprogress to let there be a duplicate file
        File file = new File("target/inprogress");
        file.mkdirs();
        FileWriter fw = new FileWriter("target/inprogress/london.txt");
        try {
            fw.write("I was there once in London");
            fw.flush();
        } finally {
            fw.close();
        }

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("file:target/reports", "Hello London", Exchange.FILE_NAME, "london.txt");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/reports?preMove=../inprogress/${file:name}&initialDelay=0&delay=10")
                        .process(new Processor() {
                            @SuppressWarnings("unchecked")
                            public void process(Exchange exchange) throws Exception {
                                GenericFile<File> file = (GenericFile<File>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
                                assertNotNull(file);
                                assertTrue(file.getRelativeFilePath().indexOf("inprogress") > -1);
                            }
                        })
                        .to("mock:report");
            }
        };
    }

}