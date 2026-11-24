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
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The scp producer sends the name of a file as given, with / as the only separator. With jailStartingDirectory (the
 * default) it refuses a name with both a backslash and a .. segment, as where such a name goes depends on the server,
 * and writes a name with a backslash and no .. segment as given.
 */
public class ScpProducerBackslashNameTest extends ScpServerTestSupport {

    private final List<String> scpCommands = new CopyOnWriteArrayList<>();

    public ScpProducerBackslashNameTest() {
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
    @ValueSource(strings = { "a\\b/../../hello.txt", "sub\\..\\hello.txt", "sub/..\\hello.txt" })
    public void testNameWithBackslashAndParentSegmentIsRefused(String name) {
        assumeTrue(isSetupComplete());

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, name));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("jailed to the starting directory"), cause.getMessage());
        // nothing is sent: the scp command is not started on the server
        assertEquals(List.of(), scpCommands, "scp commands started on the server");
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a Windows file name cannot hold a backslash")
    public void testNameWithBackslashIsWrittenAsGiven() throws Exception {
        assumeTrue(isSetupComplete());

        template.sendBodyAndHeader(getProducerUri(), "Hello World", Exchange.FILE_NAME, "a\\b.txt");

        assertEquals("Hello World", Files.readString(Paths.get(getScpPath(), "a\\b.txt")));
    }

    private String getProducerUri() {
        return getScpUri() + "?username=admin&password=admin&knownHostsFile=" + getKnownHostsFile();
    }
}
