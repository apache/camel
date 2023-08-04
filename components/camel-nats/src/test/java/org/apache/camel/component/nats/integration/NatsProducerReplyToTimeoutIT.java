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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class NatsProducerReplyToTimeoutIT extends NatsITSupport {

    protected String startUri = "direct:start";
    protected String middleUri = "nats:foo.middle?requestTimeout=50";
    protected String resultUri = "mock:result";

    protected String body1 = "Camel";

    @Test
    public void testReplyToTimeout() {
        try {
            template.requestBody(startUri, body1, String.class);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            ExchangeTimedOutException cause = assertIsInstanceOf(ExchangeTimedOutException.class, e.getCause());
            assertEquals(50, cause.getTimeout());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(startUri).to(middleUri).to(resultUri);

                from(middleUri)
                        .delayer(5000)
                        .transform(simple("Bye ${body}"));
            }
        };
    }
}
