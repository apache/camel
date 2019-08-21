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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class FileConsumerIdempotentOnExceptionHandledTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/messages/input");
        super.setUp();
    }

    @Test
    public void testIdempotent() throws Exception {
        getMockEndpoint("mock:invalid").expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/data/messages/input/", "Hello World", Exchange.FILE_NAME, "hello.txt");

        oneExchangeDone.matchesMockWaitTime();

        assertMockEndpointsSatisfied();

        // the error is handled and the file is regarded as success and
        // therefore moved to .camel
        assertFileNotExists("target/data/messages/input/hello.txt");
        assertFileExists("target/data/messages/input/.camel/hello.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                onException(Exception.class).handled(true).to("mock:invalid");

                // our route logic to process files from the input folder
                from("file:target/data/messages/input/?initialDelay=0&delay=10&idempotent=true").to("mock:input").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

}
