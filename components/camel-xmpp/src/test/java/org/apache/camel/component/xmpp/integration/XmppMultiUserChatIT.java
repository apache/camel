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
package org.apache.camel.component.xmpp.integration;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

class XmppMultiUserChatIT extends XmppBaseIT {

    private static final String BODY_1 = "the first message";
    private static final String BODY_2 = "the second message";

    @Test
    void testXmppChat() throws Exception {
        MockEndpoint consumerEndpoint = context.getEndpoint("mock:out", MockEndpoint.class);
        consumerEndpoint.expectedBodiesReceived(BODY_1, BODY_2);

        //will send chat messages to the room
        template.sendBody("direct:toProducer", BODY_1);
        Thread.sleep(50);
        template.sendBody("direct:toProducer", BODY_2);

        consumerEndpoint.setResultWaitTime(TimeUnit.MINUTES.toMillis(1));
        consumerEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                from("direct:toProducer")
                        .to(getProducerUri());

                from(getConsumerUri())
                        .to("mock:out");
            }
        };
    }

    protected String getProducerUri() {
        // the nickname parameter is necessary in these URLs because the '@' in the username can not be parsed by
        // vysper during chat room message routing.

        // here on purpose we provide the room query parameter without the domain name as 'camel-test', and Camel
        // will resolve it properly to 'camel-test@conference.apache.camel'
        return "xmpp://localhost:" + getUrl()
               + "/?connectionConfig=#customConnectionConfig&room=camel-test&user=camel_producer@apache.camel&password=secret&nickname=camel_producer";
    }

    protected String getConsumerUri() {
        // however here we provide the room query parameter as fully qualified, including the domain name as
        // 'camel-test@conference.apache.camel'
        return "xmpp://localhost:" + getUrl()
               + "/?connectionConfig=#customConnectionConfig&room=camel-test@conference.apache.camel&user=camel_consumer@apache.camel&password=secret&nickname=camel_consumer";
    }
}
