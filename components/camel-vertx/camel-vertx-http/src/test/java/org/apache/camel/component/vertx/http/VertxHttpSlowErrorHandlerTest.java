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
package org.apache.camel.component.vertx.http;

import io.vertx.core.Vertx;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class VertxHttpSlowErrorHandlerTest extends VertxHttpTestSupport {
    private static final BlockedThreadReporter reporter = new BlockedThreadReporter();
    private static final String SLOW_SERVICE_RESPONSE = "Slow Response";

    @AfterEach
    public void afterEach() {
        reporter.reset();
    }

    @Test
    void slowErrorHandlerDoesNotBlockEventLoop() throws Exception {
        Exchange result = template.request(getProducerUri() + "/test", null);
        assertFalse(result.isFailed());
        assertFalse(reporter.isEventLoopBlocked());
        assertEquals(SLOW_SERVICE_RESPONSE, result.getMessage().getBody(String.class));
    }

    @BindToRegistry
    public Vertx createVertx() {
        return createVertxWithThreadBlockedHandler(reporter);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .handled(true)
                        .maximumRedeliveries(1)
                        .redeliveryDelay(0)
                        .to("direct:slow");

                from(getTestServerUri() + "/test")
                        .to("direct:start");

                from("direct:start")
                        .removeHeaders("CamelHttp*")
                        .to(getProducerUri());

                from(getTestServerUri())
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500));

                from("direct:slow")
                        .delay(600)
                        .syncDelayed()
                        .setBody().constant(SLOW_SERVICE_RESPONSE);
            }
        };
    }
}
