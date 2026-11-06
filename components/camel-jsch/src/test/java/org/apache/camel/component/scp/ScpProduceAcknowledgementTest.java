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
package org.apache.camel.component.scp;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.scp.common.ScpTransferEventListener;
import org.apache.sshd.scp.common.helpers.DefaultScpFileOpener;
import org.apache.sshd.scp.server.ScpCommand;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The remote scp acknowledges a file once it has written it. The producer waits for that acknowledgement, so the file
 * is complete on the server when the exchange completes, and a file that the server cannot write fails the exchange.
 * <p/>
 * The remote scp sends a first acknowledgement as soon as it starts, before it reads the first protocol line. Each test
 * also runs against a server that starts the scp command late, so that this first acknowledgement arrives after the
 * channel is connected.
 */
public class ScpProduceAcknowledgementTest extends ScpServerTestSupport {

    private static final long SLOW_START_MILLIS = 500;

    private volatile boolean slowStart;
    private volatile boolean failOpen;
    private volatile boolean failWrite;

    public ScpProduceAcknowledgementTest() {
        serverConfigurer = sshd -> {
            ScpCommandFactory factory = new ScpCommandFactory() {
                @Override
                protected Command executeSupportedCommand(ChannelSession channel, String command) {
                    return new ScpCommand(
                            channel, command, resolveExecutorService(command), getSendBufferSize(),
                            getReceiveBufferSize(), getScpFileOpener(), ScpTransferEventListener.EMPTY) {
                        @Override
                        public void start(ChannelSession channel, Environment env) throws IOException {
                            if (!slowStart) {
                                super.start(channel, env);
                                return;
                            }
                            CompletableFuture.delayedExecutor(SLOW_START_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
                                try {
                                    super.start(channel, env);
                                } catch (IOException e) {
                                    LOG.warn("Cannot start the scp command", e);
                                }
                            });
                        }
                    };
                }
            };
            factory.setScpFileOpener(new DefaultScpFileOpener() {
                @Override
                public OutputStream openWrite(
                        Session session, Path file, long size, Set<PosixFilePermission> permissions, OpenOption... options)
                        throws IOException {
                    if (failOpen) {
                        throw new IOException("Permission denied");
                    }
                    OutputStream os = super.openWrite(session, file, size, permissions, options);
                    if (!failWrite) {
                        return os;
                    }
                    return new FilterOutputStream(os) {
                        @Override
                        public void write(int b) throws IOException {
                            throw new IOException("No space left on device");
                        }

                        @Override
                        public void write(byte[] b, int off, int len) throws IOException {
                            throw new IOException("No space left on device");
                        }
                    };
                }
            });
            sshd.setCommandFactory(factory);
        };
    }

    @ParameterizedTest(name = "slowStart={0}")
    @ValueSource(booleans = { false, true })
    public void testFileIsCompleteWhenTheExchangeCompletes(boolean slowStart) throws Exception {
        assumeTrue(isSetupComplete());
        this.slowStart = slowStart;

        template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertEquals("Hello World", Files.readString(Paths.get(getScpPath(), "hello.txt")));
    }

    @ParameterizedTest(name = "slowStart={0}")
    @ValueSource(booleans = { false, true })
    public void testNestedFileIsCompleteWhenTheExchangeCompletes(boolean slowStart) throws Exception {
        assumeTrue(isSetupComplete());
        this.slowStart = slowStart;

        template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, "mysub/mysubsub/hello.txt");

        assertEquals("Hello World", Files.readString(Paths.get(getScpPath(), "mysub/mysubsub/hello.txt")));
    }

    @ParameterizedTest(name = "slowStart={0}")
    @ValueSource(booleans = { false, true })
    public void testFileTheServerCannotWriteFailsTheExchange(boolean slowStart) {
        assumeTrue(isSetupComplete());
        this.slowStart = slowStart;
        failWrite = true;

        assertServerError("No space left on device");
    }

    @ParameterizedTest(name = "slowStart={0}")
    @ValueSource(booleans = { false, true })
    public void testFileTheServerCannotCreateFailsTheExchange(boolean slowStart) {
        assumeTrue(isSetupComplete());
        this.slowStart = slowStart;
        failOpen = true;

        assertServerError("Permission denied");
        assertFalse(Files.exists(Paths.get(getScpPath(), "hello.txt")), "the file is not created");
    }

    private void assertServerError(String message) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, "hello.txt"));

        GenericFileOperationFailedException cause
                = assertInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        IOException scpError = assertInstanceOf(IOException.class, cause.getCause());
        assertTrue(scpError.getMessage().contains(message), scpError.getMessage());
    }

    private String getProducerUri() {
        return getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
    }
}
