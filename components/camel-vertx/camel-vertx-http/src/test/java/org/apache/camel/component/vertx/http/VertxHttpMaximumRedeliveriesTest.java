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

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxHttpMaximumRedeliveriesTest extends VertxHttpTestSupport {

    @Test
    public void testMaximumRedeliveries() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(1 + 3); // 1 original and 3 redelivery

        Exchange exchange = template.request("direct:start", null);

        Assertions.assertTrue(exchange.isFailed());
        HttpOperationFailedException e = exchange.getException(HttpOperationFailedException.class);
        Assertions.assertNotNull(e);
        Assertions.assertEquals(500, e.getStatusCode());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(3).redeliveryDelay(0)
                        .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

                from("direct:start")
                        .to(getTestServerUri());

                from(getTestServerUri())
                        .to("mock:input")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500));
            }
        };
    }
}
