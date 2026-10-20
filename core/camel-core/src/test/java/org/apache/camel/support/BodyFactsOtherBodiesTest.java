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
package org.apache.camel.support;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A step that failed on the body says when other messages reached it with a body of another kind (CAMEL-25750): the
 * step is right for those, and the path this message took is what differs.
 */
public class BodyFactsOtherBodiesTest extends ContextTestSupport {

    @Test
    public void testTheHintSaysOtherMessagesReachedTheStepWithData() {
        // the first message takes the branch that makes data, so its quantity is read
        Exchange parsed = template.request("direct:start", e -> {
            e.getMessage().setHeader("parsed", true);
            e.getMessage().setBody("{\"qty\": 2}");
        });
        assertNull(parsed.getException());

        // the second does not, and the same read fails on the text
        Exchange text = template.request("direct:start", e -> e.getMessage().setBody("{\"qty\": 2}"));
        assertNotNull(text.getException());
        String message = text.getException().getMessage();
        assertTrue(message.contains("unmarshal it first (unmarshal: json) to read its fields; other messages reached this"
                                    + " step with data (LinkedHashMap), so the body here depends on the path a message"
                                    + " takes"),
                message);
    }

    @Test
    public void testNothingMoreWhenEveryMessageBroughtTheSameKind() {
        template.request("direct:start", e -> e.getMessage().setBody("{\"qty\": 2}"));
        Exchange text = template.request("direct:start", e -> e.getMessage().setBody("{\"qty\": 3}"));

        assertNotNull(text.getException());
        String message = text.getException().getMessage();
        assertTrue(message.contains("unmarshal it first (unmarshal: json) to read its fields"), message);
        assertFalse(message.contains("other messages reached this step"), message);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setMessageHistory(true);

                from("direct:start").routeId("orders")
                        .choice()
                        .when(header("parsed"))
                        .process(e -> e.getMessage().setBody(new LinkedHashMap<>(Map.of("qty", 2))))
                        .end()
                        .setHeader("qty", simple("${body.qty}")).id("readQty")
                        .to("mock:result");
            }
        };
    }
}
