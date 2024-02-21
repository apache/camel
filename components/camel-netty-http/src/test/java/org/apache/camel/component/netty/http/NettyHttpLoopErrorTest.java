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
package org.apache.camel.component.netty.http;

import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyHttpLoopErrorTest extends BaseNettyTest {

    @EndpointInject("direct:input")
    DirectEndpoint directEndpoint;

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                onException(NettyHttpOperationFailedException.class)
                        .maximumRedeliveries(2)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .maximumRedeliveryDelay(1000)
                        .handled(false);

                onException(HttpOperationFailedException.class)
                        .maximumRedeliveries(2)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .maximumRedeliveryDelay(1000)
                        .handled(false);

                from("direct:input")
                        .routeId("test-route")
                        .toD("${body}");
            }
        };
    }

    @Test
    void testRetryWithExchangeInOnly() {
        boolean isException = false;
        try {
            template.sendBody(directEndpoint, ExchangePattern.InOnly, "netty-http:http://example.com/return_404");
        } catch (Exception e) {
            isException = true;
        }

        assertTrue(isException);

    }

    @Test
    void testRetryWithExchangeInOut() {

        boolean isException = false;
        try {
            template.sendBody(directEndpoint, ExchangePattern.InOut, "netty-http:http://example.com/return_404");
        } catch (Exception e) {
            isException = true;
        }

        assertTrue(isException);

    }

}
