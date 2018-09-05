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
package org.apache.camel.component.stomp;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.fusesource.stomp.client.BlockingConnection;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;
import org.junit.Test;

import static org.fusesource.hawtbuf.UTF8Buffer.utf8;
import static org.fusesource.stomp.client.Constants.DESTINATION;
import static org.fusesource.stomp.client.Constants.MESSAGE_ID;
import static org.fusesource.stomp.client.Constants.SEND;

public class StompConsumerUriTest extends StompBaseTest {

    @Test
    public void testConsume() throws Exception {
        if (!canTest()) {
            return;
        }

        context.addRoutes(createRouteBuilder());
        context.start();

        Stomp stomp = createStompClient();
        final BlockingConnection producerConnection = stomp.connectBlocking();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(numberOfMessages);

        for (int i = 0; i < numberOfMessages; i++) {
            StompFrame frame = new StompFrame(SEND);
            frame.addHeader(DESTINATION, StompFrame.encodeHeader("test"));
            frame.addHeader(MESSAGE_ID, StompFrame.encodeHeader("msg:" + i));
            frame.content(utf8("Important Message " + i));
            producerConnection.send(frame);
        }

        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF("stomp:test?brokerURL=tcp://localhost:%d", getPort())
                    .transform(body().convertToString())
                    .to("mock:result");
            }
        };
    }
}
