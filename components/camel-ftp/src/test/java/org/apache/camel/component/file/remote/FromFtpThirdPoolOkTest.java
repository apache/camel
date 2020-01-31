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
package org.apache.camel.component.file.remote;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FromFtpThirdPoolOkTest extends FtpServerTestSupport {

    private static int counter;
    private String body = "Hello World this file will be deleted";

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/thirdpool?password=admin&delete=true";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/thirdpool");
        super.setUp();
    }

    @Test
    public void testPollFileAndShouldBeDeletedAtThirdPoll() throws Exception {
        template.sendBodyAndHeader(getFtpUrl(), body, Exchange.FILE_NAME, "hello.txt");

        getMockEndpoint("mock:result").expectedBodiesReceived(body);
        // 2 first attempt should fail
        getMockEndpoint("mock:error").expectedMessageCount(2);

        assertMockEndpointsSatisfied();

        // give time to delete file
        Thread.sleep(200);

        assertEquals(3, counter);

        // assert the file is deleted
        File file = new File(FTP_ROOT_DIR + "/thirdpool/hello.txt");
        assertFalse(file.exists(), "The file should have been deleted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // no redeliveries as we want the ftp consumer to try again
                // use no delay for fast unit testing
                onException(IllegalArgumentException.class).logStackTrace(false).to("mock:error");

                from(getFtpUrl()).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        counter++;
                        if (counter < 3) {
                            // file should exists
                            File file = new File(FTP_ROOT_DIR + "/thirdpool/hello.txt");
                            assertTrue(file.exists(), "The file should NOT have been deleted");
                            throw new IllegalArgumentException("Forced by unittest");
                        }
                    }
                }).to("mock:result");
            }
        };
    }
}
