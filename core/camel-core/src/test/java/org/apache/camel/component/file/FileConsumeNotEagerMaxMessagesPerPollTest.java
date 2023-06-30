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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Unit test for max messages per poll
 */
@Isolated
public class FileConsumeNotEagerMaxMessagesPerPollTest extends ContextTestSupport {

    // sort by name and not eager, then we should pickup the files in order
    private String fileUrl = fileUri("?initialDelay=0&delay=10&"
                                     + "maxMessagesPerPoll=2&eagerMaxMessagesPerPoll=false&sortBy=file:name");

    @Test
    public void testMaxMessagesPerPoll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("AAA", "BBB");

        template.sendBodyAndHeader(fileUrl, "CCC", Exchange.FILE_NAME, "ccc.FileConsumeNotEagerMaxMessagesPerPollTest.txt");
        template.sendBodyAndHeader(fileUrl, "AAA", Exchange.FILE_NAME, "aaa.FileConsumeNotEagerMaxMessagesPerPollTest.txt");
        template.sendBodyAndHeader(fileUrl, "BBB", Exchange.FILE_NAME, "bbb.FileConsumeNotEagerMaxMessagesPerPollTest.txt");

        // start route
        context.getRouteController().startRoute("foo");

        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 2);

        assertMockEndpointsSatisfied();

        mock.reset();
        mock.expectedBodiesReceived("CCC");
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUrl).routeId("foo").noAutoStartup().convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
