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
package org.apache.camel.component.file.remote.sftp.integration;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.remote.BaseServerTestSupport;
import org.apache.camel.component.file.remote.FtpConstants;
import org.apache.camel.component.file.remote.RemoteFileOperations;
import org.apache.camel.component.file.remote.SftpEndpoint;
import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A failed SFTP request gives the status of the server in the message, the code and the reason of the exception, and a
 * failed change of directory gives the current directory, that a relative path starts from. The methods of the
 * preferredAuthentications option are compared with the methods of the server in lower case, and a method that JSch
 * does not know is logged.
 */
@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
class SftpOperationFailureMessageIT extends BaseServerTestSupport {

    // the root of the server, "/", is the login directory of the user, and the ftp root directory is a directory in it
    @RegisterExtension
    SftpEmbeddedService service = new SftpEmbeddedService(true);

    private final WarnCapture warnings = new WarnCapture();

    @BeforeEach
    void captureWarnings() {
        warnings.attach();
    }

    @AfterEach
    void releaseWarnings() {
        warnings.detach();
    }

    /**
     * The path of a directory of the ftp root directory, as the server names it from the login directory.
     */
    private String dir(String name) {
        return service.getFtpRootDir().getFileName() + "/" + name;
    }

    private String uri(String directory, String options) {
        return "sftp://admin@localhost:" + service.getPort() + "/" + directory + "?password=admin&knownHostsFile="
               + service.getKnownHostsFile() + options;
    }

    private Exchange send(String uri) {
        return template.send(uri, e -> {
            e.getIn().setBody("Hello World");
            e.getIn().setHeader(Exchange.FILE_NAME, "hello.txt");
        });
    }

    @Test
    void changeDirectoryFailureGivesTheStatusAndTheCurrentDirectory() {
        Exchange out = send(uri("missing", "&autoCreate=false"));

        GenericFileOperationFailedException e
                = assertIsInstanceOf(GenericFileOperationFailedException.class, out.getException());
        SftpException cause = assertIsInstanceOf(SftpException.class, e.getCause());
        assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, cause.id);
        assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, e.getCode());
        assertTrue(e.getReason().startsWith("SSH_FX_NO_SUCH_FILE: "), e.getReason());
        assertEquals("Cannot change directory to: missing: " + e.getReason()
                     + ". The path is relative to the current directory: /",
                e.getMessage());
    }

    @Test
    void changeDirectoryFailureGivesTheDirectoryOfThePreviousSegment() {
        Exchange out = send(uri(dir("missing"), "&autoCreate=false"));

        GenericFileOperationFailedException e
                = assertIsInstanceOf(GenericFileOperationFailedException.class, out.getException());
        assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, e.getCode());
        assertEquals("Cannot change directory to: missing: " + e.getReason()
                     + ". The path is relative to the current directory: /" + service.getFtpRootDir().getFileName(),
                e.getMessage());
    }

    @Test
    void changeDirectoryToAFileGivesTheFailureThatJSchDetects() throws Exception {
        Files.createDirectories(service.getFtpRootDir());
        Files.writeString(service.getFtpRootDir().resolve("afile"), "x");

        Exchange out = send(uri(dir("afile"), "&autoCreate=false"));

        GenericFileOperationFailedException e
                = assertIsInstanceOf(GenericFileOperationFailedException.class, out.getException());
        String current = "/" + service.getFtpRootDir().getFileName();
        assertEquals(ChannelSftp.SSH_FX_FAILURE, e.getCode());
        assertEquals("SSH_FX_FAILURE: Can't change directory: " + current + "/afile", e.getReason());
        assertEquals("Cannot change directory to: afile: SSH_FX_FAILURE: Can't change directory: " + current + "/afile"
                     + ". The path is relative to the current directory: " + current,
                e.getMessage());
    }

    @Test
    void storeFailureGivesTheStatus() {
        // not stepwise, the file is written with its path, in a directory that does not exist
        Exchange out = send(uri(dir("missing"), "&autoCreate=false&stepwise=false"));

        GenericFileOperationFailedException e
                = assertIsInstanceOf(GenericFileOperationFailedException.class, out.getException());
        assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, e.getCode());
        assertTrue(e.getReason().startsWith("SSH_FX_NO_SUCH_FILE: "), e.getReason());
        assertEquals("Cannot store file: " + dir("missing/hello.txt") + ": " + e.getReason(), e.getMessage());
        // the reply headers are set as before
        assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, out.getIn().getHeader(FtpConstants.FTP_REPLY_CODE));
    }

    @Test
    void deleteAndListFailuresGiveTheStatus() {
        SftpEndpoint endpoint = context.getEndpoint(uri("", ""), SftpEndpoint.class);
        RemoteFileOperations<SftpRemoteFile> operations = endpoint.createRemoteFileOperations();
        operations.connect(endpoint.getConfiguration(), null);
        try {
            GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                    () -> operations.deleteFile("missing.txt"));
            assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, e.getCode());
            assertEquals("Cannot delete file: missing.txt: " + e.getReason(), e.getMessage());

            e = assertThrows(GenericFileOperationFailedException.class, () -> operations.listFiles("missing"));
            assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE, e.getCode());
            assertEquals("Cannot list directory: missing: " + e.getReason(), e.getMessage());
        } finally {
            operations.disconnect();
        }
    }

    @Test
    void existingDirectoryUploads() throws Exception {
        Files.createDirectories(service.getFtpRootDir().resolve("existing"));

        Exchange out = send(uri(dir("existing"), "&autoCreate=false"));

        assertNull(out.getException());
        assertTrue(Files.exists(service.getFtpRootDir().resolve("existing/hello.txt")));
    }

    @Test
    void preferredAuthenticationsInAnotherCaseLogsIn() {
        Exchange out = send(uri(dir("auth"), "&preferredAuthentications=publickey, Password"));

        assertNull(out.getException());
        assertTrue(Files.exists(service.getFtpRootDir().resolve("auth/hello.txt")));
        assertEquals(List.of(), warnings.messages());
    }

    @Test
    void unknownPreferredAuthenticationIsLoggedOncePerEndpoint() {
        String uri = uri(dir("auth"), "&preferredAuthentications=pasword,password");

        assertNull(send(uri).getException());
        assertNull(send(uri).getException());

        assertEquals(List.of("The preferredAuthentications option of sftp://admin@localhost:" + service.getPort()
                             + " has authentication methods that JSch does not know, which it skips: pasword."
                             + " The methods are password, keyboard-interactive, publickey and gssapi-with-mic."),
                warnings.messages());
    }

    /**
     * Captures the WARN messages of the sftp endpoint.
     */
    private static final class WarnCapture extends AbstractAppender {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        WarnCapture() {
            super("SftpEndpointWarnCapture", null, null, true, Property.EMPTY_ARRAY);
        }

        void attach() {
            start();
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().getRootLogger().addAppender(this, Level.WARN, null);
            ctx.updateLoggers();
        }

        void detach() {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().getRootLogger().removeAppender(getName());
            ctx.updateLoggers();
            stop();
        }

        List<String> messages() {
            return messages;
        }

        @Override
        public void append(LogEvent event) {
            if (SftpEndpoint.class.getName().equals(event.getLoggerName())) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        }
    }
}
