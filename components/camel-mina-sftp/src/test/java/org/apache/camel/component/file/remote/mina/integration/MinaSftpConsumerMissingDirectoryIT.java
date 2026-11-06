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
package org.apache.camel.component.file.remote.mina.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * With stepwise=false, the consumer lists the starting directory by its path. When it does not exist,
 * ignoreFileNotFoundOrPermissionError=true ignores it, and otherwise the poll fails with a
 * GenericFileOperationFailedException, with the whole listing as with the listing one entry at a time (CAMEL-26648).
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class MinaSftpConsumerMissingDirectoryIT extends CamelTestSupport {

    // the whole listing (no maxMessagesPerPoll, or preSort), and the listing one entry at a time
    private static final String WHOLE = "";
    private static final String WHOLE_PRE_SORT = "&maxMessagesPerPoll=10&preSort=name";
    private static final String ONE_AT_A_TIME = "&maxMessagesPerPoll=10";

    @RegisterExtension
    SftpEmbeddedService service = new SftpEmbeddedService();

    @BindToRegistry("recordingPollStrategy")
    RecordingPollStrategy pollStrategy = new RecordingPollStrategy();

    private Path base;

    @BeforeEach
    void createTestDirectory() throws IOException {
        // the root directory of the server is shared by the tests of this class and kept between runs
        base = Files.createTempDirectory(Files.createDirectories(service.getFtpRootDir()), "test");
    }

    @ParameterizedTest
    @ValueSource(strings = { WHOLE, WHOLE_PRE_SORT, ONE_AT_A_TIME })
    void ignoresMissingDirectory(String listing) throws Exception {
        consume("&ignoreFileNotFoundOrPermissionError=true" + listing);

        await().atMost(20, TimeUnit.SECONDS).until(() -> pollStrategy.polls.get() >= 3 || !pollStrategy.failures.isEmpty());
        assertEquals(List.of(), pollStrategy.failures);
    }

    @ParameterizedTest
    @ValueSource(strings = { WHOLE, WHOLE_PRE_SORT, ONE_AT_A_TIME })
    void failsPollOnMissingDirectory(String listing) throws Exception {
        consume(listing);

        await().atMost(20, TimeUnit.SECONDS).until(() -> !pollStrategy.failures.isEmpty());
        Exception failure = pollStrategy.failures.get(0);
        assertInstanceOf(GenericFileOperationFailedException.class, failure);
        SftpException sftp = ObjectHelper.getException(SftpException.class, failure);
        assertNotNull(sftp, "cause: " + failure);
        assertEquals(SftpConstants.SSH_FX_NO_SUCH_FILE, sftp.getStatus());
        // no poll completed
        assertEquals(0, pollStrategy.polls.get());
    }

    private void consume(String query) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mina-sftp://localhost:" + service.getPort() + "/" + base.resolve("missing")
                     + "?username=admin&password=admin&knownHostsFile=" + service.getKnownHostsFile()
                     + "&autoCreate=false&stepwise=false&initialDelay=0&delay=10"
                     + "&pollStrategy=#recordingPollStrategy" + query)
                        .to("mock:result");
            }
        });
    }

    /**
     * Counts the polls that completed, and records the failures of the others.
     */
    static class RecordingPollStrategy implements PollingConsumerPollStrategy {

        private final AtomicInteger polls = new AtomicInteger();
        private final List<Exception> failures = new CopyOnWriteArrayList<>();

        @Override
        public boolean begin(Consumer consumer, Endpoint endpoint) {
            return true;
        }

        @Override
        public void commit(Consumer consumer, Endpoint endpoint, int polledMessages) {
            polls.incrementAndGet();
        }

        @Override
        public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) {
            failures.add(cause);
            return false;
        }
    }
}
