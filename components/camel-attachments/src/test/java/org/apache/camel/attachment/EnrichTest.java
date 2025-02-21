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
package org.apache.camel.attachment;

import jakarta.activation.DataHandler;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnrichTest extends CamelTestSupport {

    @Test
    public void testEnrich() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "");

        MockEndpoint.assertIsSatisfied(context);

        Exchange e1 = getMockEndpoint("mock:result").getReceivedExchanges().get(0);

        AttachmentMessage am1 = e1.getMessage(AttachmentMessage.class);

        // original has 1 attachment
        Assertions.assertTrue(am1.hasAttachments());
        Assertions.assertEquals(1, am1.getAttachmentNames().size());
        Assertions.assertEquals("myfile.txt", am1.getAttachmentNames().iterator().next());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setExchangePattern(ExchangePattern.InOut)
                        .enrich("direct:enrich", new AggregationStrategy() {
                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                byte[] data = newExchange.getMessage().getBody(byte[].class);
                                String attachmentName = "myfile.txt";
                                String mimeType = "text/plain";
                                DataHandler dataHandler = new DataHandler(data, mimeType);
                                AttachmentMessage am = oldExchange.getMessage(AttachmentMessage.class);
                                am.addAttachment(attachmentName, dataHandler);
                                return oldExchange;
                            }
                        })
                        .to("mock:result");

                from("direct:enrich")
                        .setBody().constant("Hello World", byte[].class);
            }
        };
    }
}
