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
package org.apache.camel.component.file.remote;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.strategy.GenericFileRenameExclusiveReadLockStrategy;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ftp, ftps and sftp consumers use the idempotentRepository and idempotentKey options only with idempotent=true
 * (noop=true turns it on). Set alone, they do not skip any file, and creating the consumer logs a WARN that names
 * idempotent=true.
 */
class RemoteFileEndpointIdempotentOptionsTest extends CamelTestSupport {

    private static final String[] SCHEMES = { "ftp", "ftps", "sftp" };

    @BindToRegistry("repo")
    private final IdempotentRepository repo = MemoryIdempotentRepository.memoryIdempotentRepository();

    @BindToRegistry("myLock")
    private final GenericFileExclusiveReadLockStrategy<?> myLock = new GenericFileRenameExclusiveReadLockStrategy<>();

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("RemoteFileEndpointIdempotentOptions", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    static Stream<Arguments> unusedOptions() {
        return Stream.of(SCHEMES).flatMap(scheme -> Stream.of(
                Arguments.of(scheme, "idempotentRepository=#repo", "the idempotentRepository option"),
                Arguments.of(scheme, "idempotentKey=${file:onlyname}", "the idempotentKey option"),
                Arguments.of(scheme, "idempotentRepository=#repo&idempotentKey=${file:onlyname}",
                        "the idempotentRepository and idempotentKey options")));
    }

    static Stream<Arguments> usedOptions() {
        return Stream.of(SCHEMES).flatMap(scheme -> Stream.of(
                "idempotent=true&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
                "noop=true&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
                "idempotent=false&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
                "exclusiveReadLockStrategy=#myLock&idempotentKey=${file:onlyname}",
                "readLock=changed")
                .map(options -> Arguments.of(scheme, options)));
    }

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

    @ParameterizedTest
    @MethodSource("unusedOptions")
    void idempotentOptionsWithoutIdempotentLogWarn(String scheme, String options, String names) throws Exception {
        Endpoint endpoint = context.getEndpoint(scheme + "://localhost/inbox?" + options);

        endpoint.createConsumer(exchange -> {
        });

        assertEquals(List.of("The consumer of " + endpoint + " does not use " + names + ", as idempotent=true is not set:"
                             + " files already consumed are not skipped. Set idempotent=true to skip them."),
                warnings);
    }

    @ParameterizedTest
    @MethodSource("usedOptions")
    void idempotentOptionsInUseOrTurnedOffDoNotWarn(String scheme, String options) throws Exception {
        Endpoint endpoint = context.getEndpoint(scheme + "://localhost/inbox?" + options);

        endpoint.createConsumer(exchange -> {
        });

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ftp", "sftp" })
    void producerDoesNotWarn(String scheme) throws Exception {
        // the idempotent options are consumer options, so an endpoint uri shared by from and to still creates a producer
        Endpoint endpoint = context.getEndpoint(scheme + "://localhost/outbox?idempotentRepository=#repo");

        endpoint.createProducer();

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    private static Logger endpointLogger() {
        return (Logger) LogManager.getLogger(GenericFileEndpoint.class);
    }
}
