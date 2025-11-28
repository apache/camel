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
package org.apache.camel.component.file.remote.integration;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test to test aggregate with completionFromBatchConsumer option.
 */
public class FromFtpAggregateIT extends FtpServerTestSupport {

    protected String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/filter?password=admin&binary=false&noop=true";
    }

    @BeforeEach
    public void prepareFtpServer() {
        // Send multiple files to the FTP server before the Camel route starts
        sendFile(getFtpUrl(), "Message 1", "file1.txt");
        sendFile(getFtpUrl(), "Message 2", "file2.txt");
        sendFile(getFtpUrl(), "Message 3", "file3.txt");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAggregateCompletionFromBatchConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        mock.assertIsSatisfied();

        // Verify the grouped exchange contains all files
        Exchange out = mock.getExchanges().get(0);
        List<Exchange> grouped = out.getIn().getBody(List.class);

        assertEquals(3, grouped.size());
        assertEquals("Message 1", grouped.get(0).getIn().getBody(String.class));
        assertEquals("Message 2", grouped.get(1).getIn().getBody(String.class));
        assertEquals("Message 3", grouped.get(2).getIn().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getFtpUrl())
                        .aggregate(constant(true), new GroupedExchangeAggregationStrategy())
                        .completionFromBatchConsumer()
                        .eagerCheckCompletion()
                        .to("mock:result");
            }
        };
    }

}
