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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to verify that the polling consumer delivers an empty Exchange when the
 * sendEmptyMessageWhenIdle property is set and a polling event yields no
 * results.
 */
public class FtpPollingConsumerIdleMessageTest extends FtpServerTestSupport {

    @Test
    public void testConsumeIdleMessages() throws Exception {
        Thread.sleep(110);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        assertMockEndpointsSatisfied();
        assertTrue(mock.getExchanges().get(0).getIn().getBody() == null);
        assertTrue(mock.getExchanges().get(1).getIn().getBody() == null);
    }

    @BeforeEach
    public void setup() throws Exception {
        new File(FTP_ROOT_DIR + "/polling").mkdirs();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("ftp://admin@localhost:" + getPort() + "/polling?password=admin&delay=50" + "&sendEmptyMessageWhenIdle=true").to("mock:result");
            }
        };
    }

}
