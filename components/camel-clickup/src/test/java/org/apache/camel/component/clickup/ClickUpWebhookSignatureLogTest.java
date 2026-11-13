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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.component.clickup.service.ClickUpWebhookService;
import org.apache.camel.component.clickup.util.Utils;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultMessage;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The webhook signature check logs at DEBUG what it validates, but never a signature: the valid signature of an event
 * stays out of the log and out of the exception of a failed check.
 */
public class ClickUpWebhookSignatureLogTest {

    private static final String SECRET = "secret";
    private static final String BODY = "{\"event\":\"taskTimeTrackedUpdated\"}";

    private final CamelContext context = new DefaultCamelContext();
    private final ClickUpWebhookService service = new ClickUpWebhookService(null);
    private final LogEventCaptor captor = new LogEventCaptor();
    private Logger logger;
    private Level level;

    @BeforeEach
    public void captureDebugLog() {
        logger = (Logger) LogManager.getLogger(ClickUpWebhookService.class);
        level = logger.getLevel();
        Configurator.setLevel(logger.getName(), Level.DEBUG);
        logger.addAppender(captor);
    }

    @AfterEach
    public void restoreLog() {
        logger.removeAppender(captor);
        Configurator.setLevel(logger.getName(), level);
    }

    @Test
    public void validSignatureIsNotLogged() {
        String signature = Utils.computeMessageHMAC(BODY, SECRET);

        service.validateMessageSignature(message(signature), SECRET);

        assertThat(debugMessages()).anyMatch(m -> m.contains("matches"));
        assertThat(debugMessages()).anyMatch(m -> m.contains(BODY));
        assertThat(debugMessages()).noneMatch(m -> m.toLowerCase().contains(signature.toLowerCase()));
    }

    @Test
    public void calculatedSignatureIsNotLoggedNorInTheExceptionOfAFailedCheck() {
        String validSignature = Utils.computeMessageHMAC(BODY, SECRET);
        String wrongSignature = Utils.computeMessageHMAC(BODY, "another-secret");

        assertThatThrownBy(() -> service.validateMessageSignature(message(wrongSignature), SECRET))
                .isInstanceOfSatisfying(InvalidMessageSignatureException.class, e -> {
                    assertThat(e.getMessageSignature()).isEqualToIgnoringCase(wrongSignature);
                    assertThat(e.getCalculatedSignature()).isNull();
                });

        assertThat(debugMessages()).anyMatch(m -> m.contains("does not match"));
        assertThat(debugMessages()).noneMatch(m -> m.toLowerCase().contains(validSignature.toLowerCase()));
        assertThat(debugMessages()).noneMatch(m -> m.toLowerCase().contains(wrongSignature.toLowerCase()));
    }

    private Message message(String signature) {
        Message message = new DefaultMessage(context);
        message.setBody(new ByteArrayInputStream(BODY.getBytes(StandardCharsets.UTF_8)));
        message.setHeader("x-signature", signature);
        return message;
    }

    private List<String> debugMessages() {
        return captor.events.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(event -> event.getMessage().getFormattedMessage())
                .toList();
    }

    private static final class LogEventCaptor extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        LogEventCaptor() {
            super("ClickUpWebhookSignatureLogCaptor", null, null, false, Property.EMPTY_ARRAY);
            start();
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
