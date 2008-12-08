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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for consuming a batch of files (multiple files in one consume)
 */
public class FileConsumerBatchTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/idempotent");
        template.sendBodyAndHeader("file://target/file-batch/", "Hello World", FileComponent.HEADER_FILE_NAME, "hello.txt");
        template.sendBodyAndHeader("file://target/file-batch/", "Bye World", FileComponent.HEADER_FILE_NAME, "bye.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/file-batch?consumer.delay=1000").to("mock:result");
            }
        };
    }

    public void testConsumeBatch() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        // test header keys
        mock.message(0).header(FileComponent.HEADER_FILE_BATCH_TOTAL).isEqualTo(2);
        mock.message(0).header(FileComponent.HEADER_FILE_BATCH_INDEX).isEqualTo(0);
        mock.message(1).header(FileComponent.HEADER_FILE_BATCH_INDEX).isEqualTo(1);

        assertMockEndpointsSatisfied();
    }

}