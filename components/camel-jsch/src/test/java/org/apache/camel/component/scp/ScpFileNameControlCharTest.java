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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The scp producer sends the name of each directory and of the file on a protocol line that ends with a line feed, so
 * it refuses a file name with a line feed, a carriage return or a NUL character before it starts the scp command on the
 * server. Any other control character, such as a tab, is sent as is.
 * <p/>
 * The server directory is checked directly, so unlike {@link ScpSimpleProduceTest} there is no route that consumes and
 * deletes the files written there.
 */
public class ScpFileNameControlCharTest extends ScpServerTestSupport {

    private final List<String> scpCommands = new CopyOnWriteArrayList<>();

    public ScpFileNameControlCharTest() {
        // records the scp commands the server starts
        serverConfigurer = sshd -> sshd.setCommandFactory(new ScpCommandFactory() {
            @Override
            public Command createCommand(ChannelSession channel, String command) throws IOException {
                scpCommands.add(command);
                return super.createCommand(channel, command);
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a\nb.txt", "a\rb.txt", "a\u0000b.txt", "hello.txt\n",
            "a\nb/file.txt", "a\rb/file.txt", "a\u0000b/file.txt", "mysub/a\nb/file.txt" })
    public void testFileNameWithControlCharIsRefused(String name) throws Exception {
        assumeTrue(isSetupComplete());

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, name));

        assertRefused(e, name);
    }

    @Test
    public void testFileNameOptionWithControlCharIsRefused() throws Exception {
        assumeTrue(isSetupComplete());

        String uri = getProducerUri() + "&fileName=${header.target}";
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", "target", "a\nb.txt"));

        assertRefused(e, "a\nb.txt");
    }

    @Test
    public void testStoreFileDirectlyWithControlCharIsRefused() {
        ScpEndpoint endpoint = context.getEndpoint(getProducerUri(), ScpEndpoint.class);
        RemoteFileOperations<ScpFile> operations = endpoint.createRemoteFileOperations();

        GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                () -> operations.storeFileDirectly("a\nb.txt.md5", "abc"));

        assertTrue(e.getMessage().contains("character U+000A at index 1"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "a\tb.txt", "my\tsub/a\tb.txt", "a\u001Bb.txt", "a\u0085b.txt" })
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a Windows file name cannot hold a tab or an escape character")
    public void testFileNameWithOtherControlCharIsWritten(String name) throws Exception {
        assumeTrue(isSetupComplete());

        template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, name);

        assertEquals(1, scpCommands.size(), "scp commands started on the server: " + scpCommands);
        assertEquals("Hello World", Files.readString(Paths.get(getScpPath(), name)));
    }

    @Test
    public void testNestedFileName() throws Exception {
        assumeTrue(isSetupComplete());

        template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, "mysub/mysubsub/hello.txt");

        assertEquals(1, scpCommands.size(), "scp commands started on the server: " + scpCommands);
        assertEquals(List.of("mysub", "mysub/mysubsub", "mysub/mysubsub/hello.txt"), listServerFiles());
        assertEquals("Hello World", Files.readString(Paths.get(getScpPath(), "mysub/mysubsub/hello.txt")));
    }

    private void assertRefused(CamelExecutionException e, String name) throws IOException {
        GenericFileOperationFailedException cause
                = assertInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().contains("a line feed, a carriage return or a NUL character"), cause.getMessage());
        assertFalse(cause.getMessage().contains(name), "the message does not give the name");
        // nothing is sent: the scp command is not started on the server, and no file or directory is created
        assertEquals(List.of(), scpCommands, "scp commands started on the server");
        assertEquals(List.of(), listServerFiles(), "files on the server");
    }

    private String getProducerUri() {
        return getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
    }

    private List<String> listServerFiles() throws IOException {
        Path root = Paths.get(getScpPath());
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> !p.equals(root))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }
}
