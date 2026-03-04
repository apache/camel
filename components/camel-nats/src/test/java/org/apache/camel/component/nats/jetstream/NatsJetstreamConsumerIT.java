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
package org.apache.camel.component.nats.jetstream;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.nats.NatsConstants;
import org.apache.camel.component.nats.integration.NatsITSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on GitHub Actions")
@Isolated
public class NatsJetstreamConsumerIT extends NatsITSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testConsumer() throws Exception {
        mockResultEndpoint.expectedBodiesReceived("Hello World");
        mockResultEndpoint.expectedHeaderReceived(NatsConstants.NATS_SUBJECT, "mytopic");

        template.sendBody("direct:send", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri
                        = "nats:mytopic?jetstreamEnabled=true&jetstreamName=mystream&jetstreamAsync=false&durableName=camel&pullSubscription=false";

                from("direct:send")
                        // when running full test suite then send can fail due to nats server setup/teardown
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(5))
                        .to(uri);

                from(uri).to(mockResultEndpoint);
            }
        };
    }
}
