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
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.component.file.strategy.GenericFileNoOpProcessStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ftp, ftps and sftp consumers share the options of the file consumer: noop=true with delete=true deletes the
 * files, and noop=true with move does not move them. Creating the consumer logs a WARN for each.
 */
class RemoteFileEndpointConflictingConsumerOptionsTest extends CamelTestSupport {

    private static final String[] SCHEMES = { "ftp", "ftps", "sftp" };

    @BindToRegistry("myStrategy")
    private final GenericFileProcessStrategy<?> myStrategy = new GenericFileNoOpProcessStrategy<>();

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("RemoteFileEndpointConflictingConsumerOptions", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    static Stream<Arguments> conflictingOptions() {
        return Stream.of(SCHEMES).flatMap(scheme -> Stream.of(
                Arguments.of(scheme, "noop=true&delete=true",
                        " has both noop=true and delete=true: delete wins, and the files are deleted after they are"
                                                              + " processed. Remove delete=true to leave the files in"
                                                              + " place, or noop=true to delete them."),
                Arguments.of(scheme, "noop=true&move=done",
                        " has both noop=true and the move option: noop wins, and the files are not moved after they"
                                                            + " are processed. Remove noop=true to move the files, or"
                                                            + " the move option to leave them in place.")));
    }

    static Stream<Arguments> optionsThatDoWhatTheySay() {
        return Stream.of(SCHEMES).flatMap(scheme -> Stream.of(
                "noop=true", "delete=true", "move=done", "noop=true&moveFailed=error",
                "noop=true&delete=true&processStrategy=#myStrategy")
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
    @MethodSource("conflictingOptions")
    void conflictingOptionsLogWarn(String scheme, String options, String warning) throws Exception {
        Endpoint endpoint = context.getEndpoint(scheme + "://localhost/inbox?" + options);

        endpoint.createConsumer(exchange -> {
        });

        assertEquals(List.of("The consumer of " + endpoint + warning), warnings);
    }

    @ParameterizedTest
    @MethodSource("optionsThatDoWhatTheySay")
    void optionsThatDoWhatTheySayDoNotWarn(String scheme, String options) throws Exception {
        Endpoint endpoint = context.getEndpoint(scheme + "://localhost/inbox?" + options);

        endpoint.createConsumer(exchange -> {
        });

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    private static Logger endpointLogger() {
        return (Logger) LogManager.getLogger(GenericFileEndpoint.class);
    }
}
