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
package org.apache.camel.component.vertx;

import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The reply of a request/reply over the event bus must tell the sender that the route failed, and must also be sent
 * when the route returns no body.
 */
public class VertxRequestReplyFailureTest extends VertxBaseTestSupport {

    @Test
    public void testFailedRouteRepliesWithFailure() {
        Exchange out = template.request("direct:fail", e -> e.getIn().setBody("Camel"));

        ReplyException cause = assertInstanceOf(ReplyException.class, out.getException());
        assertEquals(ReplyFailure.RECIPIENT_FAILURE, cause.failureType());
        assertEquals(VertxConsumer.FAILURE_CODE, cause.failureCode());
        // the exception of the route is not sent to the sender
        assertEquals(VertxConsumer.FAILURE_MESSAGE, cause.getMessage());
    }

    @Test
    public void testNullBodyIsReplied() {
        Exchange out = template.request("direct:empty", e -> e.getIn().setBody("Camel"));

        assertNull(out.getException());
        assertNull(out.getMessage().getBody());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:fail").to("vertx:foo.fail");
                from("vertx:foo.fail")
                        .throwException(new IllegalArgumentException("Forced"));

                from("direct:empty").to("vertx:foo.empty");
                from("vertx:foo.empty")
                        .setBody(constant(null));
            }
        };
    }
}
