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

import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.component.file.FileComponent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;

public class FtpConsumer extends RemoteFileConsumer<RemoteFileExchange> {
    private static final transient Log LOG = LogFactory.getLog(FtpConsumer.class);

    private FtpEndpoint endpoint;
    private long lastPollTime;
    private FTPClient client;

    private boolean recursive = true;
    private String regexPattern;
    private boolean setNames = true;
    private boolean exclusiveRead = true;
    private boolean deleteFile;

    public FtpConsumer(FtpEndpoint endpoint, Processor processor, FTPClient client) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.client = client;
    }

    public FtpConsumer(FtpEndpoint endpoint, Processor processor, FTPClient client,
                       ScheduledExecutorService executor) {
        super(endpoint, processor, executor);
        this.endpoint = endpoint;
        this.client = client;
    }

    protected void doStart() throws Exception {
        LOG.info("Starting");
        super.doStart();
    }

    protected void doStop() throws Exception {
        LOG.info("Stopping");
        // disconnect when stopping
        try {
            disconnect();
        } catch (Exception e) {
            // ignore just log a warning
            LOG.warn("Exception occured during disconecting from " + remoteServer() + ". " +
                e.getClass().getCanonicalName() + " message: " + e.getMessage());
        }
        super.doStop();
    }

    protected void connectIfNecessary() throws IOException {
        if (!client.isConnected()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not connected, connecting to " + remoteServer());
            }
            FtpUtils.connect(client, endpoint.getConfiguration());
            LOG.info("Connected to " + remoteServer());
        }
    }

    protected void disconnect() throws IOException {
        LOG.debug("Disconnecting from " + remoteServer());
        FtpUtils.disconnect(client);
    }

    protected void poll() throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Polling " + endpoint.getConfiguration());
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
                    client.changeWorkingDirectory(fileName.substring(0, index));
                }
                // list the files in the fold and poll the first file
                final FTPFile[] files = client.listFiles(fileName.substring(index + 1));
                pollFile(files[0]);
            }
            lastPollTime = System.currentTimeMillis();
        } catch (Exception e) {
            if (isStopping() || isStopped()) {
                // if we are stopping then ignore any exception during a poll
                LOG.warn( "Consumer is stopping. Ignoring caught exception: " +
                    e.getClass().getCanonicalName() + " message: " + e.getMessage());
            } else {
                LOG.warn("Exception occured during polling: " +
                    e.getClass().getCanonicalName() + " message: " + e.getMessage());
                disconnect();
                // Rethrow to signify that we didn't poll
                throw e;
            }
        }
    }

    protected void pollDirectory(String dir) throws Exception {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Polling directory: " + dir);
        }
        String currentDir = client.printWorkingDirectory();

        client.changeWorkingDirectory(dir);
        for (FTPFile ftpFile : client.listFiles()) {
            if (ftpFile.isFile()) {
                pollFile(ftpFile);
            } else if (ftpFile.isDirectory()) {
                if (isRecursive()) {
                    pollDirectory(getFullFileName(ftpFile));
                }
            } else {
                LOG.debug("Unsupported type of FTPFile: " + ftpFile + " (not a file or directory). Is skipped.");
            }
        }

        // change back to original current dir
        client.changeWorkingDirectory(currentDir);
    }

    protected String getFullFileName(FTPFile ftpFile) throws IOException {
        return client.printWorkingDirectory() + "/" + ftpFile.getName();
    }

    private void pollFile(FTPFile ftpFile) throws Exception {
        if (ftpFile == null) {
            return;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Polling file: " + ftpFile);
        }

        long ts = ftpFile.getTimestamp().getTimeInMillis();
        // TODO do we need to adjust the TZ? can we?
        if (ts > lastPollTime && isMatched(ftpFile)) {
            String fullFileName = getFullFileName(ftpFile);

            // is we use excluse read then acquire the exclusive read (waiting until we got it)
            if (exclusiveRead) {
                acquireExclusiveRead(client, ftpFile);
            }

            // retrieve the file
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            client.retrieveFile(ftpFile.getName(), byteArrayOutputStream);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Retrieved file: " + ftpFile.getName() + " from: " + remoteServer());
            }

            RemoteFileExchange exchange = endpoint.createExchange(fullFileName, byteArrayOutputStream);

            if (isSetNames()) {
                // set the filename in the special header filename marker to the ftp filename
                String ftpBasePath = endpoint.getConfiguration().getFile();
                String relativePath = fullFileName.substring(ftpBasePath.length() + 1);
                relativePath = relativePath.replaceFirst("/", "");

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Setting exchange filename to " + relativePath);
                }
                exchange.getIn().setHeader(FileComponent.HEADER_FILE_NAME, relativePath);
            }

            if (deleteFile) {
                // delete file after consuming
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Deleteing file: " + ftpFile.getName() + " from: " + remoteServer());
                }
                boolean deleted = client.deleteFile(ftpFile.getName());
                if (!deleted) {
                    // ignore just log a warning
                    LOG.warn("Could not delete file: " + ftpFile.getName() + " from: " + remoteServer());
                }
            }

            getProcessor().process(exchange);
        }
    }

    protected void acquireExclusiveRead(FTPClient client, FTPFile ftpFile) throws IOException {
        LOG.trace("Acquiring exclusive read (avoid reading file that is in progress of being written)");

        // the trick is to try to rename the file, if we can rename then we have exclusive read
        // since its a remote file we can not use java.nio to get a RW access
        String originalName = ftpFile.getName();
        String newName = originalName + ".camelExclusiveRead";
        boolean exclusive = false;
        while (! exclusive) {
            exclusive = client.rename(originalName, newName);
            if (exclusive) {
                // rename it back so we can read it
                client.rename(newName, originalName);
            } else {
                LOG.trace("Exclusive read not granted. Sleeping for 1000 millis");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Acquired exclusive read to: " + originalName);
        }
    }

    private String remoteServer() {
        return endpoint.getConfiguration().remoteServerInformation();
    }

    protected boolean isMatched(FTPFile file) {
        boolean result = true;
        if (regexPattern != null && regexPattern.length() > 0) {
            result = file.getName().matches(regexPattern);
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Matching file: " + file.getName() + " is " + result);
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

    public boolean isExclusiveRead() {
        return exclusiveRead;
    }

    public void setExclusiveRead(boolean exclusiveRead) {
        this.exclusiveRead = exclusiveRead;
    }

    public boolean isDeleteFile() {
        return deleteFile;
    }

    public void setDeleteFile(boolean deleteFile) {
        this.deleteFile = deleteFile;
    }
}
