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
 * @version $Revision$
 */
public class FileConsumerRelativeFileNameTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/filename-consumer");
        super.setUp();
        // the file name is also starting with target/filename-consumer
        template.sendBodyAndHeader("file:target/filename-consumer", "Hello World",
                Exchange.FILE_NAME, "target/filename-consumer-hello.txt");
        template.sendBodyAndHeader("file:target/filename-consumer", "Bye World",
                Exchange.FILE_NAME, "target/filename-consumer-bye.txt");
    }

    public void testValidFilenameOnExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        // should have file name header set
        mock.allMessages().header(Exchange.FILE_NAME).isNotNull();
        // and expect name to contain target/filename-consumer-XXX.txt
        mock.message(0).header(Exchange.FILE_NAME).isEqualTo("target/filename-consumer-bye.txt");
        mock.message(1).header(Exchange.FILE_NAME).isEqualTo("target/filename-consumer-hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/filename-consumer?recursive=true&sortBy=file:name").to("mock:result");
            }
        };
    }
}
