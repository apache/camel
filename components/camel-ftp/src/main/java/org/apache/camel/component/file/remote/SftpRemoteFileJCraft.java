/**
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
package org.apache.camel.component.file.remote;

import com.jcraft.jsch.ChannelSftp;

public class SftpRemoteFileJCraft implements SftpRemoteFile<ChannelSftp.LsEntry> {

    private final ChannelSftp.LsEntry entry;

    public SftpRemoteFileJCraft(ChannelSftp.LsEntry entry) {
        this.entry = entry;
    }

    @Override
    public ChannelSftp.LsEntry getRemoteFile() {
        return entry;
    }

    @Override
    public String getFilename() {
        return entry.getFilename();
    }

    @Override
    public String getLongname() {
        return entry.getLongname();
    }

    @Override
    public boolean isDirectory() {
        return entry.getAttrs().isDir();
    }

    @Override
    public long getFileLength() {
        return entry.getAttrs().getSize();
    }

    @Override
    public long getLastModified() {
        return entry.getAttrs().getMTime() * 1000L;
    }
}
