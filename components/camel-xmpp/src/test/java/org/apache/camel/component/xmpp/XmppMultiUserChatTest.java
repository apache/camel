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
package org.apache.camel.component.xmpp;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @version 
 */
public class XmppMultiUserChatTest extends CamelTestSupport {

    protected MockEndpoint consumerEndpoint;
    protected MockEndpoint producerEndpoint;
    protected String body1 = "the first message";
    protected String body2 = "the second message";

    @Ignore
    @Test
    public void testXmppChat() throws Exception {
        // TODO: requires online against jabber. Test this manually
        consumerEndpoint = (MockEndpoint)context.getEndpoint("mock:out");
        consumerEndpoint.expectedBodiesReceived(body1, body2);

        //will send chat messages to the room
        template.sendBody("direct:toProducer", body1);
        template.sendBody("direct:toProducer", body2);

        consumerEndpoint.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                from("direct:toProducer")
                    .to(getProducerUri());

                from(getConsumerUri())
                    .to("mock:out");
            }
        };
    }

    protected String getProducerUri() {
        return "xmpp://jabber.org:5222?room=camel-test&user=camel_producer&password=secret&serviceName=jabber.org";
    }
    
    protected String getConsumerUri() {
        return "xmpp://jabber.org:5222?room=camel-test&user=camel_consumer&password=secret&serviceName=jabber.org";
    }

}
