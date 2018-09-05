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
package org.apache.camel.component.hipchat.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hipchat.HipchatConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.StatusLine;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own auth key, user, & room from https://www.hipchat.com/docs/apiv2/auth")
public class HipchatConsumerIntegrationTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);
        result.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                StatusLine status = (StatusLine)exchange.getIn().getHeader(HipchatConstants.FROM_USER_RESPONSE_STATUS);
                return 200 == status.getStatusCode();
            }
        });

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                String hipchatEndpointUri = "hipchat:http:api.hipchat.com?authToken=XXXX&consumeUsers=@ShreyasPurohit&delay=1000";

                from(hipchatEndpointUri)
                    .idempotentConsumer(
                        simple("${in.header.HipchatMessageDate} ${in.header.HipchatFromUser}"),
                        MemoryIdempotentRepository.memoryIdempotentRepository(200)
                    )
                    .to("mock:result");
            }
        };
    }

}
