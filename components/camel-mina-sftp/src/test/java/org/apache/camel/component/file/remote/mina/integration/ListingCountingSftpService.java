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

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.test.infra.ftp.services.embedded.SftpEmbeddedService;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.DirectoryHandle;
import org.apache.sshd.sftp.server.Handle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * The embedded SFTP server, counting the directory entries it sends to the clients, and the directories they open and
 * close.
 */
class ListingCountingSftpService extends SftpEmbeddedService {

    private final AtomicInteger entriesListed = new AtomicInteger();
    private final AtomicInteger directoriesOpened = new AtomicInteger();
    private final AtomicInteger directoriesClosed = new AtomicInteger();

    @Override
    public void setUpServer() throws Exception {
        super.setUpServer();
        SftpSubsystemFactory factory = (SftpSubsystemFactory) sshd.getSubsystemFactories().get(0);
        factory.addSftpEventListener(new SftpEventListener() {
            @Override
            public void open(ServerSession session, String remoteHandle, Handle localHandle) {
                if (localHandle instanceof DirectoryHandle) {
                    directoriesOpened.incrementAndGet();
                }
            }

            @Override
            public void readEntries(
                    ServerSession session, String remoteHandle, DirectoryHandle localHandle, Map<String, Path> entries) {
                entriesListed.addAndGet(entries.size());
            }

            @Override
            public void closed(ServerSession session, String remoteHandle, Handle localHandle, Throwable thrown) {
                if (localHandle instanceof DirectoryHandle) {
                    directoriesClosed.incrementAndGet();
                }
            }
        });
    }

    int entriesListed() {
        return entriesListed.get();
    }

    int directoriesOpened() {
        return directoriesOpened.get();
    }

    int directoriesClosed() {
        return directoriesClosed.get();
    }
}
