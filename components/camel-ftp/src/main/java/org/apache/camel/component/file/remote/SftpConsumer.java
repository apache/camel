/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.remote;

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.Processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

public class SftpConsumer extends RemoteFileConsumer<RemoteFileExchange> {
    private boolean recursive = true;
    private String regexPattern = "";
    private long lastPollTime = 0L;
    private final SftpEndpoint endpoint;
    private ChannelSftp channel;

    public SftpConsumer(SftpEndpoint endpoint, Processor processor, ChannelSftp channel) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.channel = channel;
    }

    public SftpConsumer(SftpEndpoint endpoint, Processor processor, ChannelSftp channel, ScheduledExecutorService executor) {
        super(endpoint, processor, executor);
        this.endpoint = endpoint;
        this.channel = channel;
    }

    protected void poll() throws Exception {
        final String fileName = endpoint.getConfiguration().getFile();
        if (endpoint.getConfiguration().isDirectory()) {
            pollDirectory(fileName);
        }
        else {
            channel.cd(fileName.substring(0, fileName.lastIndexOf('/')));
            final ChannelSftp.LsEntry file = (ChannelSftp.LsEntry) channel.ls(fileName.substring(fileName.lastIndexOf('/') + 1)).get(0);
            pollFile(file);
        }
        lastPollTime = System.currentTimeMillis();
    }

    protected void pollDirectory(String dir) throws Exception {
        channel.cd(dir);
        for (ChannelSftp.LsEntry sftpFile : (ChannelSftp.LsEntry[]) channel.ls(".").toArray(new ChannelSftp.LsEntry[]{})) {
            if (sftpFile.getFilename().startsWith(".")) {
                // skip
            }
            else if (sftpFile.getAttrs().isDir()) {
                if (isRecursive()) {
                    pollDirectory(getFullFileName(sftpFile));
                }
            }
            else {
                pollFile(sftpFile);
            }
        }
    }

    protected String getFullFileName(ChannelSftp.LsEntry sftpFile) throws IOException {
        return channel.pwd() + "/" + sftpFile.getFilename();
    }

    private void pollFile(ChannelSftp.LsEntry sftpFile) throws Exception {
        if (sftpFile.getAttrs().getMTime() * 1000 > lastPollTime) { // TODO do we need to adjust the TZ?
            if (isMatched(sftpFile)) {
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                channel.get(sftpFile.getFilename(), byteArrayOutputStream);
                getProcessor().process(endpoint.createExchange(getFullFileName(sftpFile), byteArrayOutputStream));
            }
        }
    }

    protected boolean isMatched(ChannelSftp.LsEntry sftpFile) {
        boolean result = true;
        if (regexPattern != null && regexPattern.length() > 0) {
            result = sftpFile.getFilename().matches(getRegexPattern());
        }
        return result;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public long getLastPollTime() {
        return lastPollTime;
    }

    public void setLastPollTime(long lastPollTime) {
        this.lastPollTime = lastPollTime;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }
}

