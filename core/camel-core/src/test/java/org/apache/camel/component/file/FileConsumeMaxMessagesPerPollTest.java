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

/**
 * Unit test for max messages per poll
 */
public class FileConsumeMaxMessagesPerPollTest extends ContextTestSupport {

    public static final String FILE_QUERY = "?initialDelay=0&delay=10&maxMessagesPerPoll=2";

    @Test
    public void testMaxMessagesPerPoll() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // we should poll at most 2
        mock.expectedMinimumMessageCount(2);
        mock.message(0).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(2);
        mock.message(1).exchangeProperty(Exchange.BATCH_SIZE).isEqualTo(2);
        String fileUri = fileUri(FILE_QUERY);
        template.sendBodyAndHeader(fileUri, "Bye World", Exchange.FILE_NAME, "bye.txt");
        template.sendBodyAndHeader(fileUri, "Hello World", Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeader(fileUri, "Godday World", Exchange.FILE_NAME, "godday.txt");

        // start route
        context.getRouteController().startRoute("foo");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(fileUri(FILE_QUERY)).routeId("foo").noAutoStartup().convertBodyTo(String.class).to("mock:result");
            }
        };
    }

}
