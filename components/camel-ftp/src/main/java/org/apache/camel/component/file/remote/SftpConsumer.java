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
import java.util.Vector;
import java.util.concurrent.ScheduledExecutorService;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.apache.camel.Processor;
import org.apache.camel.component.file.FileComponent;

public class SftpConsumer extends RemoteFileConsumer<RemoteFileExchange> {
    private final SftpEndpoint endpoint;

    private ChannelSftp channel;
    private Session session;

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

    protected void doStart() throws Exception {
        log.info("Starting");
        super.doStart();
    }

    protected void doStop() throws Exception {
        log.info("Stopping");
        // disconnect when stopping
        try {
            disconnect();
        } catch (Exception e) {
            // ignore just log a warning
            log.warn("Exception occured during disconecting from " + remoteServer() + ". "
                     + e.getClass().getCanonicalName() + " message: " + e.getMessage());
        }
        super.doStop();
    }

    protected void connectIfNecessary() throws JSchException {
        if (channel == null || !channel.isConnected()) {
            if (session == null || !session.isConnected()) {
                log.trace("Session isn't connected, trying to recreate and connect.");
                session = endpoint.createSession();
                session.connect();
            }
            log.trace("Channel isn't connected, trying to recreate and connect.");
            channel = endpoint.createChannelSftp(session);
            channel.connect();
            log.info("Connected to " + remoteServer());
        }
    }

    protected void disconnect() throws JSchException {
        log.debug("Disconnecting from " + remoteServer());
        if (session != null) {
            session.disconnect();
        }
        if (channel != null) {
            channel.disconnect();
        }
    }

    protected void poll() throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Polling " + endpoint.getConfiguration());
        }
        connectIfNecessary();
        // If the attempt to connect isn't successful, then the thrown
        // exception will signify that we couldn't poll
        try {
            final String fileName = endpoint.getConfiguration().getFile();
            if (endpoint.getConfiguration().isDirectory()) {
                pollDirectory(fileName);
            } else {
                int index = fileName.lastIndexOf('/');
                if (index > -1) {
                    // cd to the folder of the filename
                    channel.cd(fileName.substring(0, index));
                }

                // list the files in the fold and poll the first file
                final Vector files = channel.ls(fileName.substring(index + 1));
                final ChannelSftp.LsEntry file = (ChannelSftp.LsEntry) files.get(0);
                pollFile(file);
            }
            lastPollTime = System.currentTimeMillis();
        } catch (Exception e) {
            if (isStopping() || isStopped()) {
                // if we are stopping then ignore any exception during a poll
                log.warn("Consumer is stopping. Ignoring caught exception: "
                         + e.getClass().getCanonicalName() + " message: " + e.getMessage());
            } else {
                log.warn("Exception occured during polling: "
                         + e.getClass().getCanonicalName() + " message: " + e.getMessage());
                disconnect();
                // Rethrow to signify that we didn't poll
                throw e;
            }
        }
    }

    protected void pollDirectory(String dir) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Polling directory: " + dir);
        }
        String currentDir = channel.pwd();

        channel.cd(dir);
        Vector files = channel.ls(".");
        for (int i = 0; i < files.size(); i++) {
            ChannelSftp.LsEntry sftpFile = (ChannelSftp.LsEntry)files.get(i);
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

        // change back to original current dir
        channel.cd(currentDir);
    }

    protected String getFullFileName(ChannelSftp.LsEntry sftpFile) throws IOException, SftpException {
        return channel.pwd() + "/" + sftpFile.getFilename();
    }

    private void pollFile(ChannelSftp.LsEntry sftpFile) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Polling file: " + sftpFile);
        }

        // if using last polltime for timestamp matcing (to be removed in Camel 2.0)
        boolean timestampMatched = true;
        if (isTimestamp()) {
            // TODO do we need to adjust the TZ? can we?
            long ts = sftpFile.getAttrs().getMTime() * 1000L;
            timestampMatched = ts > lastPollTime;
            if (log.isTraceEnabled()) {
                log.trace("The file is to old + " + sftpFile + ". lastPollTime=" + lastPollTime + " > fileTimestamp=" + ts);
            }
        }

        if (timestampMatched && isMatched(sftpFile)) {
            String fullFileName = getFullFileName(sftpFile);

            // is we use excluse read then acquire the exclusive read (waiting until we got it)
            if (exclusiveReadLock) {
                acquireExclusiveReadLock(sftpFile);
            }

            // retrieve the file
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            channel.get(sftpFile.getFilename(), byteArrayOutputStream);
            if (log.isDebugEnabled()) {
                log.debug("Retrieved file: " + sftpFile.getFilename() + " from: " + remoteServer());
            }

            RemoteFileExchange exchange = endpoint.createExchange(getFullFileName(sftpFile),
                    sftpFile.getFilename(), sftpFile.getAttrs().getSize(), byteArrayOutputStream);

            if (isSetNames()) {
                String ftpBasePath = endpoint.getConfiguration().getFile();
                String relativePath = fullFileName.substring(ftpBasePath.length() + 1);
                relativePath = relativePath.replaceFirst("/", "");

                if (log.isDebugEnabled()) {
                    log.debug("Setting exchange filename to " + relativePath);
                }
                exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME, relativePath);
            }

            if (deleteFile) {
                // delete file after consuming
                if (log.isDebugEnabled()) {
                    log.debug("Deleteing file: " + sftpFile.getFilename() + " from: " + remoteServer());
                }
                deleteFile(sftpFile.getFilename());
            } else if (isMoveFile()) {
                String fromName = sftpFile.getFilename();
                String toName = getMoveFileName(fromName, exchange);
                if (log.isDebugEnabled()) {
                    log.debug("Moving file: " + fromName + " to: " + toName);
                }

                // delete any existing file
                boolean deleted = deleteFile(toName);
                if (!deleted) {
                    // if we could not delete any existing file then maybe the folder is missing
                    // build folder if needed
                    int lastPathIndex = toName.lastIndexOf('/');
                    if (lastPathIndex != -1) {
                        String directory = toName.substring(0, lastPathIndex);
                        if (!SftpUtils.buildDirectory(channel, directory)) {
                            log.warn("Can not build directory: " + directory + " (maybe because of denied permissions)");
                        }
                    }
                }

                // try to rename
                try {
                    channel.rename(fromName, toName);
                } catch (SftpException e) {
                    // ignore just log a warning
                    log.warn("Can not move file: " + fromName + " to: " + toName);
                }
            }

            getProcessor().process(exchange);
        }
    }

    private boolean deleteFile(String filename) {
        try {
            channel.rm(filename);
            return true;
        } catch (SftpException e) {
            // ignore just log a warning
            log.warn("Could not delete file: " + filename + " from: " + remoteServer());
            return false;
        }
    }

    protected void acquireExclusiveReadLock(ChannelSftp.LsEntry sftpFile) throws SftpException {
        if (log.isTraceEnabled()) {
            log.trace("Waiting for exclusive read lock to file: " + sftpFile);
        }

        // the trick is to try to rename the file, if we can rename then we have exclusive read
        // since its a remote file we can not use java.nio to get a RW access
        String originalName = sftpFile.getFilename();
        String newName = originalName + ".camelExclusiveReadLock";
        boolean exclusive = false;
        while (!exclusive) {
            try {
                channel.rename(originalName, newName);
                exclusive = true;
            } catch (SftpException e) {
                // ignore we can not rename it
            }

            if (exclusive) {
                if (log.isDebugEnabled()) {
                    log.debug("Acquired exclusive read lock to file: " + originalName);
                }
                // rename it back so we can read it
                channel.rename(newName, originalName);
            } else {
                log.trace("Exclusive read lock not granted. Sleeping for 1000 millis");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    protected String getFileName(Object file) {
        ChannelSftp.LsEntry sftpFile = (ChannelSftp.LsEntry) file;
        return sftpFile.getFilename();
    }

}
