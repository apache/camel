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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.SftpSubsystem;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * An SFTP server whose root is a local directory and whose login directory is a directory below it, as on most servers,
 * and that can report a listed file or directory under another name.
 */
final class HomeDirectorySftpServer {

    private HomeDirectorySftpServer() {
    }

    /**
     * @param root    the local directory that is the root of the server
     * @param home    the login directory, an absolute path on the server
     * @param renames the names to report in a listing instead of the names of the files and directories
     */
    static SshServer start(Path root, String home, Map<String, String> renames) throws IOException {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(root.resolveSibling("hostkey.ser")));
        sshd.setPasswordAuthenticator((username, password, session) -> true);
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(root.toAbsolutePath()));
        SftpSubsystemFactory factory = new SftpSubsystemFactory() {
            @Override
            public Command createSubsystem(ChannelSession channel) throws IOException {
                return new SftpSubsystem(channel, this) {
                    @Override
                    public Path getDefaultDirectory() {
                        return fileSystem.getPath(home);
                    }

                    @Override
                    protected void writeDirEntry(
                            int id, DirectoryHandle dir, Map<String, Path> entries, Buffer buffer, int index, Path f,
                            String shortName, LinkOption... options)
                            throws IOException {
                        super.writeDirEntry(id, dir, entries, buffer, index, f, renames.getOrDefault(shortName, shortName),
                                options);
                    }
                };
            }
        };
        sshd.setSubsystemFactories(List.of(factory));
        sshd.start();
        return sshd;
    }
}
