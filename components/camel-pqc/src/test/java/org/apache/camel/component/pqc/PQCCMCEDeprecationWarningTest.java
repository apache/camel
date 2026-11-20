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
package org.apache.camel.component.pqc;

import java.security.Security;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.component.pqc.dataformat.PQCDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CMCE (Classic McEliece) key encapsulation algorithm is deprecated: an endpoint or a data format that uses it logs
 * a WARN.
 * <p>
 * Isolated: the WARN is captured with an appender on the root logger configuration, which is shared with the test
 * classes that run in parallel.
 */
@Isolated
public class PQCCMCEDeprecationWarningTest {

    private final CamelContext context = new DefaultCamelContext();
    private final LogEventCaptor captor = new LogEventCaptor();

    @BeforeAll
    public static void addProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider(BouncyCastlePQCProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    @BeforeEach
    public void captureLog() {
        captor.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().addAppender(captor, Level.WARN, null);
        ctx.updateLoggers();
        context.start();
    }

    @AfterEach
    public void restoreLog() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().removeAppender(captor.getName());
        ctx.updateLoggers();
        captor.stop();
        context.stop();
    }

    @Test
    public void cmceEndpointLogsWarn() {
        context.getEndpoint(
                "pqc:keyenc?operation=generateSecretKeyEncapsulation&symmetricKeyAlgorithm=AES&keyEncapsulationAlgorithm=CMCE");

        assertEquals(1, cmceWarnings().size(), captor.events::toString);
    }

    @Test
    public void mlkemEndpointLogsNoWarn() {
        context.getEndpoint(
                "pqc:keyenc?operation=generateSecretKeyEncapsulation&symmetricKeyAlgorithm=AES&keyEncapsulationAlgorithm=MLKEM");

        assertTrue(cmceWarnings().isEmpty(), captor.events::toString);
    }

    @Test
    public void cmceDataFormatLogsWarn() {
        PQCDataFormat dataFormat = new PQCDataFormat();
        dataFormat.setKeyEncapsulationAlgorithm("CMCE");
        dataFormat.start();
        try {
            assertEquals(1, cmceWarnings().size(), captor.events::toString);
        } finally {
            dataFormat.stop();
        }
    }

    @Test
    public void mlkemDataFormatLogsNoWarn() {
        PQCDataFormat dataFormat = new PQCDataFormat();
        dataFormat.start();
        try {
            assertTrue(cmceWarnings().isEmpty(), captor.events::toString);
        } finally {
            dataFormat.stop();
        }
    }

    private List<String> cmceWarnings() {
        return captor.events.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(event -> event.getMessage().getFormattedMessage())
                .filter(message -> message.contains("CMCE") && message.contains("deprecated"))
                .toList();
    }

    private static final class LogEventCaptor extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        LogEventCaptor() {
            super("PQCCMCEDeprecationWarningCaptor-" + UUID.randomUUID(), null, null, false, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            String loggerName = event.getLoggerName();
            if (PQCComponent.class.getName().equals(loggerName) || PQCDataFormat.class.getName().equals(loggerName)) {
                events.add(event.toImmutable());
            }
        }
    }
}
