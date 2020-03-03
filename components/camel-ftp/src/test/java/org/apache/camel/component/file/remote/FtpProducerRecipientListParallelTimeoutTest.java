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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Unit test to verify that Camel can build remote directory on FTP server if
 * missing (full or part of).
 */
@Disabled("Run this test manually")
public class FtpProducerRecipientListParallelTimeoutTest extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin:admin@127.0.0.2:" + (getPort() - 1) + "/timeout";
    }

    @Test
    public void testRecipientListTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        // B will timeout so we only get A and C
        mock.expectedBodiesReceived("AC");

        template.sendBodyAndHeader("direct:start", "Hello", "slip", "direct:a," + getFtpUrl() + ",direct:c");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getShutdownStrategy().setTimeout(60);

                from("direct:start").recipientList(header("slip")).aggregationStrategy(new AggregationStrategy() {
                    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                        if (oldExchange == null) {
                            return newExchange;
                        }

                        String body = oldExchange.getIn().getBody(String.class);
                        oldExchange.getIn().setBody(body + newExchange.getIn().getBody(String.class));
                        return oldExchange;
                    }
                }).parallelProcessing().timeout(2000).to("mock:result");

                from("direct:a").setBody(constant("A"));

                from("direct:c").delay(500).setBody(constant("C"));
            }
        };
    }

}
