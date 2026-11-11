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
package org.apache.camel.component.clickup;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.clickup.service.ClickUpWebhookService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;

class ClickUpWebhookProcessorTest {

    @Test
    void eventThatCannotBeReadFailsTheExchange() throws Exception {
        AtomicBoolean next = new AtomicBoolean();
        ClickUpWebhookService service = new ClickUpWebhookService(null) {
            @Override
            public void validateMessageSignature(Message message, String secret) {
                // accepted
            }
        };
        ClickUpWebhookProcessor processor
                = new ClickUpWebhookProcessor(exchange -> next.set(true), service, "secret");

        try (CamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8)));
            AtomicBoolean done = new AtomicBoolean();

            processor.process(exchange, doneSync -> done.set(true));

            assertThat(done).isTrue();
            assertThat(next).isFalse();
            assertThat(exchange.getException()).isInstanceOf(JacksonException.class);
        }
    }
}
