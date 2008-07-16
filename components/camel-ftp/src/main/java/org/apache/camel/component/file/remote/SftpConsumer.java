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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.apache.camel.Processor;
import org.apache.camel.component.file.FileComponent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class SftpConsumer extends RemoteFileConsumer<RemoteFileExchange> {
    private static final transient Log LOG = LogFactory.getLog(SftpConsumer.class);
    private final SftpEndpoint endpoint;
    private boolean recursive = true;
    private String regexPattern = "";
    private long lastPollTime;

    private ChannelSftp channel;
    private Session session;
    private boolean setNames;

    public SftpConsumer(SftpEndpoint endpoint, Processor processor, Session session) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.session = session;
    }

    public SftpConsumer(SftpEndpoint endpoint, Processor processor, Session session, ScheduledExecutorService executor) {
        super(endpoint, processor, executor);
        this.endpoint = endpoint;
        this.session = session;
    }

    protected void connectIfNecessary() throws JSchException {
        if (channel == null || !channel.isConnected()) {
            if (session == null || !session.isConnected()) {
                LOG.info("Session isn't connected, trying to recreate and connect...");
                session = endpoint.createSession();
                session.connect();
            }
            LOG.info("Channel isn't connected, trying to recreate and connect...");
            channel = endpoint.createChannelSftp(session);
            channel.connect();
            LOG.info("Connected to " + endpoint.getConfiguration().toString());
        }
    }

    protected void disconnect() throws JSchException {
        if (session != null) {
            LOG.info("Session is being explicitly disconnected");
            session.disconnect();
        }
        if (channel != null) {
            LOG.info("Channel is being explicitly disconnected");
            channel.disconnect();
        }
    }

    protected void poll() throws Exception {
        // TODO: is there a way to avoid copy-pasting the reconnect logic?
        connectIfNecessary();
        // If the attempt to connect isn't successful, then the thrown
        // exception will signify that we couldn't poll
        try {
            final String fileName = endpoint.getConfiguration().getFile();
            if (endpoint.getConfiguration().isDirectory()) {
                pollDirectory(fileName);
            } else {
                channel.cd(fileName.substring(0, fileName.lastIndexOf('/')));
                final ChannelSftp.LsEntry file = (ChannelSftp.LsEntry)channel.ls(fileName.substring(fileName.lastIndexOf('/') + 1)).get(0);
                pollFile(file);
            }
            lastPollTime = System.currentTimeMillis();
        } catch (JSchException e) {
            // If the connection has gone stale, then we must manually disconnect
            // the client before attempting to reconnect
            LOG.warn("Disconnecting due to exception: " + e.toString());
            disconnect();
            // Rethrow to signify that we didn't poll
            throw e;
        } catch (SftpException e) {
            // Still not sure if/when these come up and what we should do about them
            // client.disconnect();
            LOG.warn("Caught SftpException:" + e.toString());
            LOG.warn("Doing nothing for now, need to determine an appropriate action");
            // Rethrow to signify that we didn't poll
            throw e;
        }
    }

    protected void pollDirectory(String dir) throws Exception {
        channel.cd(dir);
        for (ChannelSftp.LsEntry sftpFile : (ChannelSftp.LsEntry[])channel.ls(".").toArray(new ChannelSftp.LsEntry[] {})) {
            if (sftpFile.getFilename().startsWith(".")) {
                // skip
            } else if (sftpFile.getAttrs().isDir()) {
                if (isRecursive()) {
                    pollDirectory(getFullFileName(sftpFile));
                }
            } else {
                pollFile(sftpFile);
            }
        }
    }

    protected String getFullFileName(ChannelSftp.LsEntry sftpFile) throws IOException, SftpException {
        return channel.pwd() + "/" + sftpFile.getFilename();
    }

    private void pollFile(ChannelSftp.LsEntry sftpFile) throws Exception {
        if (sftpFile.getAttrs().getMTime() * 1000L > lastPollTime) {
            if (isMatched(sftpFile)) {
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                channel.get(sftpFile.getFilename(), byteArrayOutputStream);
                RemoteFileExchange exchange = endpoint.createExchange(getFullFileName(sftpFile), byteArrayOutputStream);

                if (isSetNames()) {
                    String relativePath = getFullFileName(sftpFile).substring(endpoint.getConfiguration().getFile().length());
                    if (relativePath.startsWith("/")) {
                        relativePath = relativePath.substring(1);
                    }
                    exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME, relativePath);
                }

                getProcessor().process(exchange);
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

    public boolean isSetNames() {
        return setNames;
    }

    public void setSetNames(boolean setNames) {
        this.setNames = setNames;
    }
}
