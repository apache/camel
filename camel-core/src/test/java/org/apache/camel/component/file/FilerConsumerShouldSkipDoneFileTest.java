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
public class FilerConsumerShouldSkipDoneFileTest extends ContextTestSupport {

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("target/done");
        super.setUp();
    }

    public void testDoneFile() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        // write the done file
        template.sendBodyAndHeader("file:target/done", "", Exchange.FILE_NAME, "done");

        // wait a bit and it should not pickup the written file as there are no target file
        Thread.sleep(250);

        assertMockEndpointsSatisfied();
        resetMocks();
        oneExchangeDone.reset();

        // done file should exist
        File file = new File("target/done/done");
        assertTrue("Done file should exist: " + file, file.exists());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        // write the target file
        template.sendBodyAndHeader("file:target/done", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        // done file should be deleted now
        assertFalse("Done file should be deleted: " + file, file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/done?doneFileName=done&initialDelay=0&delay=10")
                    .convertBodyTo(String.class)
                    .to("mock:result");
            }
        };
    }

}
