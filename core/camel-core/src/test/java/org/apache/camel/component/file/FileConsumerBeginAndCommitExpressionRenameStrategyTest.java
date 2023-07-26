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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the FileRenameStrategy using preMoveExpression and expression options
 */
public class FileConsumerBeginAndCommitExpressionRenameStrategyTest extends ContextTestSupport {

    @Test
    public void testRenameSuccess() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:report");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Hello Paris");
        mock.expectedFileExists(testFile("done/paris.bak"), "Hello Paris");

        template.sendBodyAndHeader(fileUri("reports"), "Hello Paris", Exchange.FILE_NAME, "paris.txt");

        mock.assertIsSatisfied();
    }

    @Test
    public void testIllegalOptions() {

        Endpoint ep = context.getEndpoint(fileUri("?move=../done/${file:name}&delete=true"));

        Assertions.assertThrows(IllegalArgumentException.class, () -> ep.createConsumer(exchange -> {
        }), "Should have thrown an exception");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri(
                        "reports?preMove=../inprogress/${file:name.noext}.bak&move=../done/${file:name}&initialDelay=0&delay=10"))
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
