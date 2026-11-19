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
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.strategy.GenericFileRenameExclusiveReadLockStrategy;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file consumer uses the idempotentRepository and idempotentKey options only with idempotent=true (noop=true turns
 * it on) or with an idempotent read lock. Set alone, they do not skip any file, and the consumer logs a WARN that names
 * idempotent=true. The consumer still consumes the file then, as before.
 */
// isolated: the test captures the log of GenericFileEndpoint
@Isolated
class FileConsumerIdempotentOptionsWithoutIdempotentTest extends ContextTestSupport {

    private static final String LOGGER = GenericFileEndpoint.class.getName();

    private final MemoryIdempotentRepository repo = new MemoryIdempotentRepository();
    private final PollCounter polls = new PollCounter();
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        repo.start();
        registry.bind("repo", repo);
        registry.bind("polls", polls);
        registry.bind("myLock", new GenericFileRenameExclusiveReadLockStrategy<>());
        return registry;
    }

    @BeforeEach
    void captureWarnings() {
        ConsumingAppender.newAppender(LOGGER, getClass().getSimpleName(), Level.WARN, event -> {
            if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                warnings.add(event.getMessage().getFormattedMessage());
            }
        });
    }

    @AfterEach
    void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
    }

    @Test
    void idempotentRepositoryWithoutIdempotentLogsWarn() throws Exception {
        repo.add(testFile("a.txt").toAbsolutePath().toString());
        Files.writeString(testFile("a.txt"), "a");

        Endpoint endpoint = route("idempotentRepository=#repo");

        // the repository is not used: the file is consumed although its key is in the repository
        getMockEndpoint("mock:result").expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, warnings.size(), "warnings: " + warnings);
        assertEquals("The consumer of " + endpoint + " does not use the idempotentRepository option, as idempotent=true"
                     + " is not set: files already consumed are not skipped. Set idempotent=true to skip them.",
                warnings.get(0));
    }

    @Test
    void idempotentKeyWithoutIdempotentLogsWarn() throws Exception {
        Files.writeString(testFile("a.txt"), "a");

        Endpoint endpoint = route("idempotentKey=${file:onlyname}");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, warnings.size(), "warnings: " + warnings);
        assertEquals("The consumer of " + endpoint + " does not use the idempotentKey option, as idempotent=true"
                     + " is not set: files already consumed are not skipped. Set idempotent=true to skip them.",
                warnings.get(0));
    }

    @Test
    void idempotentRepositoryAndKeyWithoutIdempotentLogsWarn() throws Exception {
        repo.add("a.txt");
        Files.writeString(testFile("a.txt"), "a");

        Endpoint endpoint = route("idempotentRepository=#repo&idempotentKey=${file:onlyname}");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, warnings.size(), "warnings: " + warnings);
        assertEquals("The consumer of " + endpoint + " does not use the idempotentRepository and idempotentKey options,"
                     + " as idempotent=true is not set: files already consumed are not skipped. Set idempotent=true to"
                     + " skip them.",
                warnings.get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "idempotent=true&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
            "noop=true&idempotentRepository=#repo&idempotentKey=${file:onlyname}" })
    void idempotentConsumerSkipsTheFileWithoutWarn(String options) throws Exception {
        repo.add("a.txt");
        Files.writeString(testFile("a.txt"), "a");

        route(options);

        await().atMost(10, TimeUnit.SECONDS).until(() -> polls.getPolls() >= 5);
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());
        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "readLock=idempotent&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
            "readLock=idempotent-changed&readLockCheckInterval=10&idempotentRepository=#repo",
            "readLock=idempotent-rename&idempotentRepository=#repo",
            "idempotent=false&idempotentRepository=#repo&idempotentKey=${file:onlyname}",
            "exclusiveReadLockStrategy=#myLock&idempotentKey=${file:onlyname}" })
    void optionsInUseOrIdempotentTurnedOffDoNotWarn(String options) throws Exception {
        Files.writeString(testFile("a.txt"), "a");

        route(options);

        getMockEndpoint("mock:result").expectedMessageCount(1);
        MockEndpoint.assertIsSatisfied(context);
        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    private Endpoint route(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?" + options + "&initialDelay=0&delay=10&pollStrategy=#polls")).routeId("r")
                        .to("mock:result");
            }
        });
        context.start();
        return context.getRoute("r").getEndpoint();
    }

    /**
     * Counts the completed polls, so a test can wait for a number of polls before asserting that nothing was consumed.
     */
    private static final class PollCounter extends DefaultPollingConsumerPollStrategy {

        private final AtomicInteger polls = new AtomicInteger();

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            polls.incrementAndGet();
        }

        int getPolls() {
            return polls.get();
        }
    }
}
