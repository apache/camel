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
 * Unit tests to ensure that when the option allowNullBody is set to true then
 * If the fileExist option is set to Append the file's contents will not be modified
 * If the fileExist option is set to Override the file's contents will be empty
 */
public class FileProducerAllowNullBodyFileAlreadyExistsTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/allow");
        super.setUp();
        template.sendBodyAndHeader("file://target/allow", "Hello world", Exchange.FILE_NAME, "hello.txt");
    }

    public void testFileExistAppendAllowNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:appendTypeAppendResult");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/allow/hello.txt", "Hello world");

        template.sendBody("direct:appendTypeAppend", null);

        assertMockEndpointsSatisfied();
    }
    
    public void testFileExistOverrideAllowNullBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:appendTypeOverrideResult");
        mock.expectedMessageCount(1);
        mock.expectedFileExists("target/allow/hello.txt", "");

        template.sendBody("direct:appendTypeOverride", null);

        assertMockEndpointsSatisfied();
    }
    
    
    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:appendTypeAppend")
                    .setHeader(Exchange.FILE_NAME, constant("hello.txt"))
                    .to("file://target/allow?allowNullBody=true&fileExist=Append")
                    .to("mock:appendTypeAppendResult");
                
                from("direct:appendTypeOverride")
                     .setHeader(Exchange.FILE_NAME, constant("hello.txt"))
                     .to("file://target/allow?allowNullBody=true&fileExist=Override")
                     .to("mock:appendTypeOverrideResult");
            }
        };
    }
}
