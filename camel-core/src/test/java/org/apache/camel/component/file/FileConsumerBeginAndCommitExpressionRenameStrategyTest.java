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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;

/**
 * Unit test for the FileRenameStrategy using preMoveExpression and expression options
 */
public class FileConsumerBeginAndCommitExpressionRenameStrategyTest extends ContextTestSupport {

    public void testRenameSuccess() throws Exception {
        deleteDirectory("target/inprogress");
        deleteDirectory("target/done");
        deleteDirectory("target/reports");

        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Paris");

        template.sendBodyAndHeader("file:target/reports", "Hello Paris", FileComponent.HEADER_FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();

        // sleep to let the file consumer do its renaming
        Thread.sleep(100);

        // content of file should be Hello Paris
        String content = IOConverter.toString(new File("./target/done/paris.bak"));
        assertEquals("The file should have been renamed", "Hello Paris", content);
    }

    public void testIllegalOptions() throws Exception {
        try {
            context.getEndpoint("file://target?expression=../done/${file:name}&delete=true").createConsumer(new Processor() {
                public void process(Exchange exchange) throws Exception {
                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/reports?preMoveExpression=../inprogress/${file:name.noext}.bak&expression=../done/${file:name}&consumer.delay=5000")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                FileExchange fe = (FileExchange) exchange;
                                assertEquals("The file should have been move to inprogress",
                                        "inprogress", fe.getFile().getParentFile().getName());
                            }
                        })
                        .to("mock:report");
            }
        };
    }

}