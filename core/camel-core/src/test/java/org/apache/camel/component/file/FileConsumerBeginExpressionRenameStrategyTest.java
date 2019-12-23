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
import java.io.FileWriter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for the FileRenameStrategy using preMoveExpression options
 */
public class FileConsumerBeginExpressionRenameStrategyTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/inprogress");
        deleteDirectory("target/data/reports");
        super.setUp();
    }

    @Test
    public void testRenameSuccess() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("file:target/data/reports", "Hello Paris", Exchange.FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();
    }

    @Test
    public void testRenameFileExists() throws Exception {
        // create a file in inprogress to let there be a duplicate file
        File file = new File("target/data/inprogress");
        file.mkdirs();
        FileWriter fw = new FileWriter("target/data/inprogress/london.bak");
        try {
            fw.write("I was there once in London");
            fw.flush();
        } finally {
            fw.close();
        }

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedBodiesReceived("Hello London");

        template.sendBodyAndHeader("file:target/data/reports", "Hello London", Exchange.FILE_NAME, "london.txt");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/data/reports?preMove=../inprogress/${file:name.noext}.bak&initialDelay=0&delay=10").process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(Exchange exchange) throws Exception {
                        GenericFile<File> file = (GenericFile<File>)exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
                        assertNotNull(file);
                        assertTrue(file.getRelativeFilePath().indexOf("inprogress") > -1);
                    }
                }).to("mock:report");
            }
        };
    }

}
