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
package org.apache.camel.component.ssh;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class SshComponentErrorHandlingTest extends SshComponentTestSupport {

    @Test
    public void testRedelivery() throws Exception {
        final String msg = "test";

        MockEndpoint mockError = getMockEndpoint("mock:error");
        mockError.expectedMinimumMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:success");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived(msg);
        mock.expectedHeaderReceived(Exchange.REDELIVERED, true);

        sshd.stop();

        template.sendBody("direct:redeliver", msg);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error")
                        .maximumRedeliveries(3)
                        .redeliveryDelay(0L) // speedup unit test by not waiting between redeliveries
                        .onRedelivery(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                final Message in = exchange.getIn();
                                final int count = in.getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
                                final int maxCount = in.getHeader(Exchange.REDELIVERY_MAX_COUNTER, Integer.class);

                                log.info("Redelivery count = {}", count);

                                // Restart the sshd server before the last redelivery attempt
                                if (count >= (maxCount - 1)) {
                                    if (sshd != null) {
                                        sshd.start();
                                        log.info("Restarting SSHD");
                                    }
                                }
                            }
                        }));

                from("direct:redeliver")
                        .tracing()
                        .to("ssh://smx:smx@localhost:" + port)
                        .to("mock:success");
            }
        };
    }
}
