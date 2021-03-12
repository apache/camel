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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for the FileRenameStrategy using preMove and move options
 */
public class FileConsumerBeginAndCommitRenameStrategyTest extends ContextTestSupport {

    @Test
    public void testRenameSuccess() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Paris");
        mock.expectedFileExists(testFile("done/paris.txt"), "Hello Paris");

        template.sendBodyAndHeader(fileUri("reports"), "Hello Paris", Exchange.FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();
    }

    @Test
    public void testIllegalOptions() throws Exception {
        try {
            context.getEndpoint(fileUri("?move=../done/${file:name}&delete=true")).createConsumer(new Processor() {
                public void process(Exchange exchange) throws Exception {
                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            // ok
        }

        try {
            context.getEndpoint(fileUri("?move=${file:name.noext}.bak&delete=true")).createConsumer(new Processor() {
                public void process(Exchange exchange) throws Exception {
                }
            });
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri("reports?preMove=../inprogress/${file:name}&move=../done/${file:name}&initialDelay=0&delay=10"))
                        .process(new Processor() {
                            @SuppressWarnings("unchecked")
                            public void process(Exchange exchange) throws Exception {
                                GenericFile<File> file
                                        = (GenericFile<File>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
                                assertNotNull(file);
                                assertTrue(file.getRelativeFilePath().contains("inprogress"));
                            }
                        }).to("mock:report");
            }
        };
    }

}
