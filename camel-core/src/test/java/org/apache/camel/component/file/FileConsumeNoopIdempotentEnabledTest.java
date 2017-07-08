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
 * @version 
 */
public class FileConsumeNoopIdempotentEnabledTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/noop");
        super.setUp();
    }

    public void testNoop() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // should only be able to read the file once as idempotent is true
        mock.expectedMessageCount(1);

        template.sendBodyAndHeader("file://target/noop", "Hello World", Exchange.FILE_NAME, "hello.txt");

        // give some time to let consumer try to read the file multiple times
        Thread.sleep(50);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/noop?noop=true&idempotent=true&initialDelay=0&delay=10").convertBodyTo(String.class).to("mock:result");
            }
        };
    }
}