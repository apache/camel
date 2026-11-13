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
package org.apache.camel.component.whatsapp;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.whatsapp.model.MessageResponse;
import org.apache.camel.component.whatsapp.model.TextMessage;
import org.apache.camel.component.whatsapp.model.TextMessageRequest;
import org.apache.camel.component.whatsapp.service.WhatsAppServiceRestAPIAdapter;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * At INFO the producer logs only the status of the provider response, not its body. The body stays available at DEBUG
 * for diagnostics and the parsed response still reaches the route.
 */
class WhatsAppProducerResponseLogTest extends CamelTestSupport {

    // values that appear in the response body but must not be echoed into the INFO log
    private static final String RECIPIENT = "15550009999";
    private static final String MESSAGE_ID = "wamid.TESTONLYMARKER";

    private HttpServer server;

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = ("{\"messaging_product\":\"whatsapp\","
                           + "\"contacts\":[{\"input\":\"" + RECIPIENT + "\",\"wa_id\":\"" + RECIPIENT + "\"}],"
                           + "\"messages\":[{\"id\":\"" + MESSAGE_ID + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @Override
    protected void cleanupResources() {
        server.stop(0);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        WhatsAppComponent component = new WhatsAppComponent();
        component.setAuthorizationToken("token");
        context.addComponent("whatsapp", component);
        return context;
    }

    @Test
    void logsTheStatusButNotTheResponseBodyAtInfo() throws Exception {
        Logger adapterLogger = (Logger) LogManager.getLogger(WhatsAppServiceRestAPIAdapter.class);
        LogEventCaptor captor = new LogEventCaptor();
        adapterLogger.addAppender(captor);

        Exchange exchange;
        try {
            exchange = template.asyncSend("direct:server", e -> e.getIn().setBody(textMessage()))
                    .get(30, TimeUnit.SECONDS);
        } finally {
            adapterLogger.removeAppender(captor);
        }

        // functionality preserved: the parsed response still reaches the route
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isInstanceOf(MessageResponse.class);

        List<String> infoMessages = captor.events.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(event -> event.getMessage().getFormattedMessage())
                .toList();

        // the status is still reported at INFO ...
        assertThat(infoMessages).anyMatch(message -> message.contains("200"));
        // ... but the response body (recipient and message identifiers) is not
        assertThat(infoMessages).noneMatch(message -> message.contains(RECIPIENT));
        assertThat(infoMessages).noneMatch(message -> message.contains(MESSAGE_ID));
    }

    private static TextMessageRequest textMessage() {
        TextMessageRequest request = new TextMessageRequest();
        request.setTo(RECIPIENT);
        request.setText(new TextMessage());
        request.getText().setBody("Hello");
        return request;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:server").to("whatsapp:123?baseUri=http://localhost:" + server.getAddress().getPort());
            }
        };
    }

    private static final class LogEventCaptor extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        LogEventCaptor() {
            super("WhatsAppProducerResponseLogCaptor", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
