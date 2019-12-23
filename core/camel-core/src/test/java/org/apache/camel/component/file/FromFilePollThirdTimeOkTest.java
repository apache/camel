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
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class FromFilePollThirdTimeOkTest extends ContextTestSupport {

    private static int counter;
    private String body = "Hello World this file will be deleted";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/deletefile");
        super.setUp();
    }

    @Test
    public void testPollFileAndShouldBeDeletedAtThirdPoll() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        template.sendBodyAndHeader("file://target/data/deletefile", body, Exchange.FILE_NAME, "hello.txt");
        context.getRouteController().startRoute("FromFilePollThirdTimeOkTest");

        getMockEndpoint("mock:result").expectedBodiesReceived(body);

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());
        assertEquals(3, counter);

        // assert the file is deleted
        File file = new File("target/data/deletefile/hello.txt");
        assertFalse("The file should have been deleted", file.exists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("file://target/data/deletefile?delete=true&initialDelay=0&delay=10").noAutoStartup().routeId("FromFilePollThirdTimeOkTest").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        counter++;
                        if (counter < 3) {
                            // file should exists
                            File file = new File("target/data/deletefile/hello.txt");
                            assertTrue("The file should NOT have been deleted", file.exists());
                            throw new IllegalArgumentException("Forced by unittest");
                        }
                    }
                }).convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
