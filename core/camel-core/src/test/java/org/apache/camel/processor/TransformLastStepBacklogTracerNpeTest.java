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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A specialized (subclass of {@link DefaultMessage}) IN message, such as the one platform-http or http create, must
 * stay attached to the exchange when a {@code transform} EIP is the last step of a route and the backlog tracer is
 * active (or in standby mode with message history enabled, as camel-debug sets it up). Otherwise
 * BacklogTracerRouteAdvice#after fails with a NullPointerException while dumping the IN message.
 */
class TransformLastStepBacklogTracerNpeTest extends ContextTestSupport {

    @Test
    void transformAsLastStepWithSpecializedMessage() {
        Exchange out = sendSpecializedMessage("direct:transformLast", "Hello");

        assertThat(out.getException()).as("route must not fail").isNull();
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("Hello World");
    }

    @Test
    void transformAsLastStepWithPlainDefaultMessage() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello");

        Exchange out = template.send("direct:transformLast", exchange);

        assertThat(out.getException()).as("route must not fail").isNull();
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("Hello World");
    }

    @Test
    void transformFollowedByAnotherStepWithSpecializedMessage() {
        Exchange out = sendSpecializedMessage("direct:transformFollowed", "Hello");

        assertThat(out.getException()).as("route must not fail").isNull();
        assertThat(out.getMessage().getBody(String.class)).isEqualTo("Hello World");
    }

    private Exchange sendSpecializedMessage(String uri, String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.setIn(new SpecializedMessage(context));
        exchange.getIn().setBody(body);
        return template.send(uri, exchange);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setUseBreadcrumb(false);
                context.setBacklogTracingStandby(true);
                context.setMessageHistory(true);

                from("direct:transformLast")
                        .transform(simple("${body} World"));

                from("direct:transformFollowed")
                        .transform(simple("${body} World"))
                        .removeHeader("X-Does-Not-Exist");
            }
        };
    }

    /**
     * Mimics HttpMessage (platform-http, http, etc.): a specialized message that is not exactly a
     * {@link DefaultMessage}, forcing TransformProcessor to copy it into a new DefaultMessage.
     */
    private static class SpecializedMessage extends DefaultMessage {
        SpecializedMessage(CamelContext camelContext) {
            super(camelContext);
        }
    }
}
