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
package org.apache.camel.component.smb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The smb consumer uses the idempotentRepository and idempotentKey options only with idempotent=true (noop=true turns
 * it on). Set alone, they do not skip any file, and creating the consumer logs a WARN that names idempotent=true.
 */
class SmbEndpointIdempotentOptionsTest extends CamelTestSupport {

    @BindToRegistry("repo")
    private final IdempotentRepository repo = MemoryIdempotentRepository.memoryIdempotentRepository();

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("SmbEndpointIdempotentOptions", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    @BeforeEach
    void captureWarnings() {
        appender.start();
        endpointLogger().addAppender(appender);
        // addAppender creates a non-additive logger config, which would hide this logger from the root appenders
        endpointLogger().setAdditive(true);
    }

    @AfterEach
    void removeAppender() {
        endpointLogger().removeAppender(appender);
        appender.stop();
    }

    @Test
    void idempotentRepositoryWithoutIdempotentLogsWarn() throws Exception {
        Endpoint endpoint
                = context.getEndpoint("smb:localhost/share?idempotentRepository=#repo&idempotentKey=${file:onlyname}");

        endpoint.createConsumer(exchange -> {
        });

        assertEquals(List.of("The consumer of " + endpoint + " does not use the idempotentRepository and idempotentKey"
                             + " options, as idempotent=true is not set: files already consumed are not skipped."
                             + " Set idempotent=true to skip them."),
                warnings);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "idempotent=true&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
            "noop=true&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
            "idempotent=false&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
            "readLock=changed" })
    void idempotentOptionsInUseOrTurnedOffDoNotWarn(String options) throws Exception {
        Endpoint endpoint = context.getEndpoint("smb:localhost/share?" + options);

        endpoint.createConsumer(exchange -> {
        });

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    private static Logger endpointLogger() {
        return (Logger) LogManager.getLogger(GenericFileEndpoint.class);
    }
}
