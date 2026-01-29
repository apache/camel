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
package org.apache.camel.component.file.remote.mina;

import org.apache.camel.component.file.remote.SftpRemoteFile;
import org.apache.sshd.sftp.client.SftpClient;

/**
 * Adapter for Apache MINA SSHD's DirEntry to implement SftpRemoteFile interface.
 */
public class MinaSftpRemoteFile implements SftpRemoteFile<SftpClient.DirEntry> {

    private final SftpClient.DirEntry entry;

    public MinaSftpRemoteFile(SftpClient.DirEntry entry) {
        this.entry = entry;
    }

    @Override
    public SftpClient.DirEntry getRemoteFile() {
        return entry;
    }

    @Override
    public String getFilename() {
        return entry.getFilename();
    }

    @Override
    public String getLongname() {
        return entry.getLongFilename();
    }

    @Override
    public boolean isDirectory() {
        return entry.getAttributes().isDirectory();
    }

    @Override
    public long getFileLength() {
        return entry.getAttributes().getSize();
    }

    @Override
    public long getLastModified() {
        // MINA SSHD returns FileTime, convert to millis
        if (entry.getAttributes().getModifyTime() != null) {
            return entry.getAttributes().getModifyTime().toMillis();
        }
        return 0L;
    }
}
