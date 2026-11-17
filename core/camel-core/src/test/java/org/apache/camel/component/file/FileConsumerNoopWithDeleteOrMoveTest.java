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
import org.apache.camel.component.file.strategy.GenericFileNoOpProcessStrategy;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultPollingConsumerPollStrategy;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * noop=true with delete=true deletes the files (delete wins), and noop=true with move does not move them (noop wins).
 * Both start as before, and the consumer logs a WARN that says which option wins.
 */
// isolated: the test captures the log of GenericFileEndpoint
@Isolated
class FileConsumerNoopWithDeleteOrMoveTest extends ContextTestSupport {

    private static final String LOGGER = GenericFileEndpoint.class.getName();

    private final PollCounter polls = new PollCounter();
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("polls", polls);
        registry.bind("myStrategy", new GenericFileNoOpProcessStrategy<>());
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
    void noopWithDeleteLogsWarnAndDeletesTheFile() throws Exception {
        Files.writeString(testFile("a.txt"), "a");

        Endpoint endpoint = route("noop=true&delete=true");

        getMockEndpoint("mock:result").expectedBodiesReceived("a");
        MockEndpoint.assertIsSatisfied(context);
        // delete wins, as before
        await().atMost(10, TimeUnit.SECONDS).until(() -> !Files.exists(testFile("a.txt")));

        assertEquals(List.of("The consumer of " + endpoint + " has both noop=true and delete=true: delete wins, and the"
                             + " files are deleted after they are processed. Remove delete=true to leave the files in"
                             + " place, or noop=true to delete them."),
                warnings);
    }

    @Test
    void noopWithMoveLogsWarnAndDoesNotMoveTheFile() throws Exception {
        Files.writeString(testFile("a.txt"), "a");

        Endpoint endpoint = route("noop=true&move=done");

        getMockEndpoint("mock:result").expectedBodiesReceived("a");
        MockEndpoint.assertIsSatisfied(context);
        // the file is committed before the next poll: it is still in place, as before
        int start = polls.getPolls();
        await().atMost(10, TimeUnit.SECONDS).until(() -> polls.getPolls() >= start + 3);
        assertTrue(Files.exists(testFile("a.txt")));
        assertFalse(Files.exists(testFile("done/a.txt")));

        assertEquals(List.of("The consumer of " + endpoint + " has both noop=true and the move option: noop wins, and"
                             + " the files are not moved after they are processed. Remove noop=true to move the files,"
                             + " or the move option to leave them in place."),
                warnings);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "noop=true", "delete=true", "move=done", "noop=true&preMove=work", "noop=true&moveFailed=error",
            "noop=true&delete=true&processStrategy=#myStrategy" })
    void optionsThatDoWhatTheySayDoNotWarn(String options) throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("?" + options));

        endpoint.createConsumer(exchange -> {
        });

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    private Endpoint route(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?" + options + "&initialDelay=0&delay=10&pollStrategy=#polls")).routeId("r")
                        .convertBodyTo(String.class).to("mock:result");
            }
        });
        context.start();
        return context.getRoute("r").getEndpoint();
    }

    /**
     * Counts the completed polls, so a test can wait for a number of polls after a file was processed.
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
