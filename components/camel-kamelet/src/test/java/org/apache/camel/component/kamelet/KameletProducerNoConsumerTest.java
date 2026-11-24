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
package org.apache.camel.component.kamelet;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The message of {@link KameletConsumerNotAvailableException} says why there is no consumer: the route of the Kamelet
 * and its status, or that there is no such route, how long the producer waited, and the options to change it.
 */
@Timeout(20)
public class KameletProducerNoConsumerTest extends CamelTestSupport {

    @Test
    public void testKameletRouteStopped() throws Exception {
        context.getRouteController().stopRoute("echo");

        assertThat(failure("direct:start"))
                .startsWith("No consumers available on endpoint: kamelet:echo/echo?timeout=100. ")
                .contains("Route echo of Kamelet echo in CamelContext " + context.getName()
                          + " consumes from it and is Stopped")
                .contains("The producer waited 100 millis for a consumer")
                .contains("block, timeout and failIfNoConsumers options");
    }

    @Test
    public void testKameletRouteRemoved() throws Exception {
        context.getRouteController().stopRoute("echo");
        context.removeRoute("echo");

        assertThat(failure("direct:start"))
                .startsWith("No consumers available on endpoint: kamelet:echo/echo?timeout=100. ")
                .contains("CamelContext " + context.getName() + " has no route echo of Kamelet echo")
                .contains("The producer waited 100 millis for a consumer");
    }

    @Test
    public void testProducerDoesNotWait() throws Exception {
        context.getRouteController().stopRoute("quiet");

        assertThat(failure("direct:no-wait"))
                .startsWith("No consumers available on endpoint: kamelet:echo/quiet?block=false. ")
                .contains("Route quiet of Kamelet echo in CamelContext " + context.getName()
                          + " consumes from it and is Stopped")
                .contains("The producer did not wait for a consumer")
                .doesNotContain("waited");
    }

    @Test
    public void testKameletRouteStarted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:kamelet");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();
    }

    private String failure(String uri) {
        CamelExecutionException e
                = catchThrowableOfType(CamelExecutionException.class, () -> template.sendBody(uri, "Hello World"));
        assertThat(e).as("The exchange should fail").isNotNull();
        assertThat(e.getCause()).isInstanceOf(KameletConsumerNotAvailableException.class);
        return e.getCause().getMessage();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("echo")
                        .from("kamelet:source")
                        .to("mock:kamelet");

                from("direct:start")
                        .to("kamelet:echo/echo?timeout=100");

                from("direct:no-wait")
                        .to("kamelet:echo/quiet?block=false");
            }
        };
    }
}
