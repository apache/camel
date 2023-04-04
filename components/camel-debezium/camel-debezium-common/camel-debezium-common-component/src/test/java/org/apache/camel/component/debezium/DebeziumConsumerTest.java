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
package org.apache.camel.component.debezium;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import io.debezium.util.Collect;
import io.debezium.util.IoUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.component.debezium.configuration.FileConnectorEmbeddedDebeziumConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DebeziumConsumerTest extends CamelTestSupport {

    private static final int NUMBER_OF_LINES = 5;
    private static final String DEFAULT_DATA_TESTING_FOLDER = "target/data";
    private static final Path TEST_FILE_PATH = createTestingPath("camel-debezium-test-file-input.txt").toAbsolutePath();
    private static final Path TEST_OFFSET_STORE_PATH
            = createTestingPath("camel-debezium-test-offset-store.txt").toAbsolutePath();
    private static final String DEFAULT_TOPIC_NAME = "test_name_dummy";
    private static final String DEFAULT_ROUTE_ID = "foo";

    private File inputFile;
    private File offsetStore;
    private int linesAdded;

    @EndpointInject("mock:result")
    private MockEndpoint to;

    @BeforeEach
    public void beforeEach() {
        linesAdded = 0;
        inputFile = createTestingFile(TEST_FILE_PATH);
        offsetStore = createTestingFile(TEST_OFFSET_STORE_PATH);
    }

    @AfterEach
    public void afterEach() {
        // clean all data files
        deletePath(TEST_FILE_PATH);
        deletePath(TEST_OFFSET_STORE_PATH);
    }

    @AfterAll
    public static void afterClass() {
        // make sure to clean all data files
        deletePath(TEST_FILE_PATH);
        deletePath(TEST_OFFSET_STORE_PATH);
    }

    @Test
    void camelShouldConsumeDebeziumMessages() throws Exception {
        // add initial lines to the file
        appendLinesToSource(NUMBER_OF_LINES);

        // assert exchanges
        to.expectedMessageCount(linesAdded);
        to.expectedHeaderReceived(DebeziumConstants.HEADER_IDENTIFIER, DEFAULT_TOPIC_NAME);
        to.expectedBodiesReceivedInAnyOrder("message-1", "message-2", "message-3", "message-4", "message-5");

        // verify the first records if they being consumed
        to.assertIsSatisfied(50);

        // send another batch
        appendLinesToSource(NUMBER_OF_LINES);

        // assert exchanges again
        to.expectedMessageCount(linesAdded);
        to.expectedHeaderReceived(DebeziumConstants.HEADER_IDENTIFIER, DEFAULT_TOPIC_NAME);

        to.assertIsSatisfied(50);
    }

    @Test
    void camelShouldContinueConsumeDebeziumMessagesWhenRouteIsOffline() throws Exception {
        // add initial lines to the file
        appendLinesToSource(NUMBER_OF_LINES);

        // assert exchanges
        to.expectedMessageCount(linesAdded);

        // verify the first records if they being consumed
        to.assertIsSatisfied(50);

        // assert when route if off
        to.reset();

        // stop route
        context.getRouteController().stopRoute(DEFAULT_ROUTE_ID);

        // send a batch while the route is off
        appendLinesToSource(NUMBER_OF_LINES);

        // start route again
        context.getRouteController().startRoute(DEFAULT_ROUTE_ID);

        // assert exchange messages after restarting, it should continue using the offset file
        to.expectedMessageCount(NUMBER_OF_LINES);
        to.expectedHeaderReceived(DebeziumConstants.HEADER_IDENTIFIER, DEFAULT_TOPIC_NAME);
        to.expectedBodiesReceivedInAnyOrder("message-6", "message-7", "message-8", "message-9", "message-10");

        to.assertIsSatisfied(50);

    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final DebeziumComponent component = new DebeziumTestComponent(context);

        component.setConfiguration(initConfiguration());
        context.addComponent("debezium", component);

        context.disableJMX();

        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("debezium:dummy")
                        .to(to);
            }
        };
    }

    private static Path createTestingPath(final String relativePath) {
        return Paths.get(DEFAULT_DATA_TESTING_FOLDER, relativePath).toAbsolutePath();
    }

    private static File createTestingFile(final Path relativePath) {
        return IoUtil.createFile(relativePath.toAbsolutePath());
    }

    private static void deletePath(final Path path) {
        try {
            IoUtil.delete(path);
        } catch (IOException e) {
            System.err.printf("Unable to delete %s%n", path.toAbsolutePath());
        }
    }

    private EmbeddedDebeziumConfiguration initConfiguration() {
        final FileConnectorEmbeddedDebeziumConfiguration configuration = new FileConnectorEmbeddedDebeziumConfiguration();
        configuration.setName("test_name_dummy");
        configuration.setTopicConfig(DEFAULT_TOPIC_NAME);
        configuration.setOffsetStorageFileName(TEST_OFFSET_STORE_PATH.toAbsolutePath().toString());
        configuration.setTestFilePath(TEST_FILE_PATH);
        configuration.setOffsetFlushIntervalMs(0);

        return configuration;
    }

    private void appendLinesToSource(int numberOfLines) throws IOException {
        CharSequence[] lines = new CharSequence[numberOfLines];
        for (int i = 0; i != numberOfLines; ++i) {
            lines[i] = generateLine(linesAdded + i + 1);
        }
        java.nio.file.Files.write(inputFile.toPath(), Collect.arrayListOf(lines), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        linesAdded += numberOfLines;
    }

    private String generateLine(int lineNumber) {
        return "message-" + lineNumber;
    }
}
