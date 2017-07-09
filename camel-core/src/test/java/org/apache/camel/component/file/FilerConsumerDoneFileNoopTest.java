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
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for writing done files
 */
public class FilerConsumerDoneFileNoopTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/done");
        super.setUp();
    }

    public void testDoneFile() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        // wait a bit and it should not pickup the written file as there are no done file
        getMockEndpoint("mock:result").setResultMinimumWaitTime(50);

        template.sendBodyAndHeader("file:target/done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        resetMocks();
        oneExchangeDone.reset();

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        // write the done file
        template.sendBodyAndHeader("file:target/done", "", Exchange.FILE_NAME, "done");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        // done file should be kept now
        File file = new File("target/done/done");
        assertTrue("Done file should be not be deleted: " + file, file.exists());

        // as well the original file should be kept due noop
        file = new File("target/done/hello.txt");
        assertTrue("Original file should be kept: " + file, file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/done?initialDelay=0&delay=10&doneFileName=done&noop=true").to("mock:result");
            }
        };
    }

}
