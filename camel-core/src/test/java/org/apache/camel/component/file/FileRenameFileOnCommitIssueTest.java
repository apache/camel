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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class FileRenameFileOnCommitIssueTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/renameissue");
        super.setUp();
    }

    public void testFileRenameFileOnCommitIssue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/renameissue/.camel/hello.txt");

        template.sendBodyAndHeader("file://target/renameissue", "World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/renameissue?noop=false&initialDelay=0&delay=10")
                    .setProperty("PartitionID").simple("${file:name}")
                    .convertBodyTo(String.class)
                    .inOut("direct:source")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            log.info("The exchange's IN body as String is {}", exchange.getIn().getBody(String.class));
                        }
                    })
                    .to("mock:result");

                from("direct:source").transform(body().prepend("Hello "));
            }
        };
    }
}
