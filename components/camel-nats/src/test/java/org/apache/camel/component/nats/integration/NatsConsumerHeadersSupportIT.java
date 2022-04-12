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
package org.apache.camel.component.nats.integration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

class NatsConsumerHeadersSupportIT extends NatsITSupport {

    private static final String HEADER_KEY_1 = "header1";

    private static final String HEADER_KEY_2 = "header2";

    private static final String HEADER_VALUE_1 = "value1";

    private static final String HEADER_VALUE_2 = "value2";

    private static final String HEADER_VALUE_3 = "value3";

    @EndpointInject("mock:results")
    protected MockEndpoint mockResultEndpoint;

    @Test
    void testConsumerShouldForwardHeaders() throws IOException, InterruptedException {

        List<String> secondHeaders = new ArrayList<String>();
        secondHeaders.add(HEADER_VALUE_2);
        secondHeaders.add(HEADER_VALUE_3);
        this.mockResultEndpoint.expectedHeaderReceived(HEADER_KEY_1, HEADER_VALUE_1);
        this.mockResultEndpoint.expectedHeaderReceived(HEADER_KEY_2, secondHeaders);

        final Options options = new Options.Builder().server("nats://" + service.getServiceAddress()).build();
        final Connection connection = Nats.connect(options);

        final Headers headers = new Headers();
        headers.add(HEADER_KEY_1, HEADER_VALUE_1);
        headers.add(HEADER_KEY_2, HEADER_VALUE_2, HEADER_VALUE_3);

        final NatsMessage message = NatsMessage.builder()
                .data("Hello World".getBytes())
                .subject("test")
                .headers(headers)
                .build();

        connection.publish(message);

        this.mockResultEndpoint.assertIsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                this.from("nats:test").to(NatsConsumerHeadersSupportIT.this.mockResultEndpoint);
            }
        };
    }

}
