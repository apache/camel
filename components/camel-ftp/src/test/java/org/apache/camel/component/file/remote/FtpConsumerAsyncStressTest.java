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

import java.util.Random;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FtpConsumerAsyncStressTest extends FtpServerTestSupport {

    private int files = 100;

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort() + "/filestress/?password=admin&maxMessagesPerPoll=25";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        for (int i = 0; i < files; i++) {
            template.sendBodyAndHeader("file://" + FTP_ROOT_DIR + "/filestress", "Hello World", Exchange.FILE_NAME, i + ".txt");
        }
    }

    @Test
    public void testFTPConsumerAsyncStress() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // give some time to run on slower boxes
        mock.setResultWaitTime(30000);
        mock.expectedMinimumMessageCount(50);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // leverage the fact that we can limit to max 25 files per poll
                // this will result in polling again and potentially picking up
                // files
                // that already are in progress
                from(getFtpUrl()).threads(10).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // simulate some work with random time to complete
                        Random ran = new Random();
                        int delay = ran.nextInt(500) + 10;
                        Thread.sleep(delay);
                    }
                }).to("mock:result");
            }
        };
    }
}
