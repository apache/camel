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
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fileName option of a consumer is a file name, not a pattern: fileName=*.csv only consumes a file named *.csv. The
 * consumer still starts, and logs a WARN that points to antInclude.
 */
// isolated: the test captures the log of GenericFileEndpoint
@Isolated
class FileConsumerFileNameWildcardTest extends ContextTestSupport {

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
    void fileNameWithWildcardLogsWarnAndMatchesNoFile() throws Exception {
        Files.writeString(testFile("a.csv"), "a");

        Endpoint endpoint = route("fileName=*.csv&noop=true");

        // the name is compared as is, as before
        await().atMost(10, TimeUnit.SECONDS).until(() -> polls.getPolls() >= 5);
        assertEquals(0, getMockEndpoint("mock:result").getReceivedCounter());

        assertEquals(List.of("The consumer of " + endpoint + " only consumes a file named exactly *.csv: the fileName"
                             + " option is a file name, not a pattern. To consume the files that match *.csv use"
                             + " antInclude=*.csv (or include with a regular expression) instead of fileName."),
                warnings);
    }

    @Test
    void fileNameWithQuestionMarkLogsWarn() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("?fileName=a?.csv"));

        endpoint.createConsumer(exchange -> {
        });

        assertEquals(1, warnings.size(), "warnings: " + warnings);
        assertTrue(warnings.get(0).contains("use antInclude=a?.csv"), warnings.get(0));
    }

    @Test
    void antIncludeConsumesTheFilesThatMatch() throws Exception {
        Files.writeString(testFile("a.csv"), "a");
        Files.writeString(testFile("b.txt"), "b");

        route("antInclude=*.csv&noop=true");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedHeaderReceived(Exchange.FILE_NAME, "a.csv");
        MockEndpoint.assertIsSatisfied(context);
        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    @ParameterizedTest
    @ValueSource(strings = { "fileName=a.csv", "fileName=a-${date:now:yyyyMMdd}.csv", "fileName=${file:onlyname}" })
    void fileNameWithoutWildcardDoesNotWarn(String options) throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("?" + options));

        endpoint.createConsumer(exchange -> {
        });

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }

    @Test
    void producerDoesNotWarn() throws Exception {
        Endpoint endpoint = context.getEndpoint(fileUri("?fileName=*.csv"));

        endpoint.createProducer();

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
