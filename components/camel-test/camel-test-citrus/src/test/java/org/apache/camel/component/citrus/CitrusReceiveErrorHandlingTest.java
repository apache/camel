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
package org.apache.camel.component.citrus;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitrusReceiveErrorHandlingTest extends CamelTestSupport {

    @Test
    void validateOperationDoesNotTriggerOnException() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:validate/test?messageType=json", CitrusEndpoint.class);
        MockEndpoint handled = context.getEndpoint("mock:handled", MockEndpoint.class);

        citrus.validateAll().body("{\"action\": \"expected\"}");
        citrus.expectedMessageCount(1);
        handled.expectedMessageCount(0);

        template.sendBody("direct:validate", "{\"action\": \"wrong\"}");

        handled.assertIsSatisfied();
        assertThat(citrus.getReceivedCounter()).isEqualTo(1);
        assertThat(citrus.getFailures()).hasSize(1);
        assertThatThrownBy(citrus::assertIsSatisfied)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void receiveOperationTriggersOnException() throws Exception {
        CitrusEndpoint citrus = context.getEndpoint("citrus:receive/service?messageType=json", CitrusEndpoint.class);
        MockEndpoint handled = context.getEndpoint("mock:handled", MockEndpoint.class);

        citrus.when().receiveAll()
                .body("{\"action\": \"expected\"}")
                .then().respond()
                .body("{\"result\": \"ok\"}");
        citrus.expectedMessageCount(1);
        handled.expectedMessageCount(1);

        template.send("direct:receive", exchange -> {
            exchange.getIn().setBody("{\"action\": \"wrong\"}");
        });

        handled.assertIsSatisfied();
        assertThat(citrus.getReceivedCounter()).isEqualTo(1);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .handled(true)
                        .to("mock:handled");

                from("direct:validate")
                        .to("citrus:validate/test?messageType=json");

                from("direct:receive")
                        .to("citrus:receive/service?messageType=json");
            }
        };
    }
}
