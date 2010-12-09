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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class FromFileDoNotMoveFileIfProcessFailsTest extends ContextTestSupport {

    private String body = "Hello World this file will NOT be moved";

    @Override
    protected void setUp() throws Exception {
        deleteDirectory("./target/movefile");
        super.setUp();
    }

    public void testPollFileAndShouldNotBeMoved() throws Exception {
        template.sendBodyAndHeader("file://target/movefile", body, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedMessageCount(1);
        mock.message(0).body(String.class).isEqualTo(body);

        mock.assertIsSatisfied();
        oneExchangeDone.matchesMockWaitTime();

        // assert the file is not moved
        File file = new File("./target/movefile/hello.txt");
        file = file.getAbsoluteFile();
        assertTrue("The file should NOT have been moved", file.exists());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false).handled(false));

                from("file://target/movefile?move=done").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        throw new IllegalArgumentException("Forced by unittest");
                    }
                });
            }
        };
    }


}