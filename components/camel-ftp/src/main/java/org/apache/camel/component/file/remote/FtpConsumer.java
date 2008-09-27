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
import org.apache.camel.component.file.FileComponent;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

public class FtpConsumer extends RemoteFileConsumer<RemoteFileExchange> {

    private FtpEndpoint endpoint;
    private FTPClient client;
    private boolean loggedIn;

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
            String message = "Could not disconnect from " + remoteServer()
                    + ". Reason: " + client.getReplyString() + ". Code: " + client.getReplyCode();
            log.warn(message);
        }
        super.doStop();
    }

    protected void connectIfNecessary() throws IOException {
        if (!client.isConnected() || !loggedIn) {
            if (log.isDebugEnabled()) {
                log.debug("Not connected/logged in, connecting to " + remoteServer());
            }
            loggedIn = FtpUtils.connect(client, endpoint.getConfiguration());
            if (!loggedIn) {
                return;
            }
        }
        
        log.info("Connected and logged in to " + remoteServer());
    }

    protected void disconnect() throws IOException {
        loggedIn = false;
        log.debug("Disconnecting from " + remoteServer());
        FtpUtils.disconnect(client);
    }

    protected void poll() throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("Polling " + endpoint.getConfiguration());
        }

        try {
            connectIfNecessary();

            if (!loggedIn) {
                String message = "Could not connect/login to " + endpoint.getConfiguration();
                log.warn(message);
                throw new FtpOperationFailedException(client.getReplyCode(), client.getReplyString(), message);
            }

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
            loggedIn = false;
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
                log.debug("Unsupported type of FTPFile: " + ftpFile + " (not a file or directory). It is skipped.");
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

        if (log.isTraceEnabled()) {
            log.trace("Polling file: " + ftpFile);
        }

        // if using last polltime for timestamp matcing (to be removed in Camel 2.0)
        boolean timestampMatched = true;
        if (isTimestamp()) {
            // TODO do we need to adjust the TZ? can we?
            long ts = ftpFile.getTimestamp().getTimeInMillis();
            timestampMatched = ts > lastPollTime;
            if (log.isTraceEnabled()) {
                log.trace("The file is to old + " + ftpFile + ". lastPollTime=" + lastPollTime + " > fileTimestamp=" + ts);
            }
        }

        if (timestampMatched && isMatched(ftpFile)) {
            String fullFileName = getFullFileName(ftpFile);

            // is we use excluse read then acquire the exclusive read (waiting until we got it)
            if (exclusiveReadLock) {
                acquireExclusiveReadLock(client, ftpFile);
            }

            // retrieve the file
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            client.retrieveFile(ftpFile.getName(), byteArrayOutputStream);
            if (log.isDebugEnabled()) {
                log.debug("Retrieved file: " + ftpFile.getName() + " from: " + remoteServer());
            }

            RemoteFileExchange exchange = endpoint.createExchange(fullFileName, ftpFile.getName(),
                    ftpFile.getSize(), byteArrayOutputStream);

            if (isSetNames()) {
                // set the filename in the special header filename marker to the ftp filename
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
                    log.debug("Deleteing file: " + ftpFile.getName() + " from: " + remoteServer());
                }
                boolean deleted = client.deleteFile(ftpFile.getName());
                if (!deleted) {
                    String message = "Can not delete file: " + ftpFile.getName() + " from: " + remoteServer();
                    throw new FtpOperationFailedException(client.getReplyCode(), client.getReplyString(), message);
                }
            } else if (isMoveFile()) {
                String fromName = ftpFile.getName();
                String toName = getMoveFileName(fromName, exchange);
                if (log.isDebugEnabled()) {
                    log.debug("Moving file: " + fromName + " to: " + toName);
                }

                // delete any existing file
                boolean deleted = client.deleteFile(toName);
                if (!deleted) {
                    // if we could not delete any existing file then maybe the folder is missing
                    // build folder if needed
                    int lastPathIndex = toName.lastIndexOf('/');
                    if (lastPathIndex != -1) {
                        String directory = toName.substring(0, lastPathIndex);
                        if (!FtpUtils.buildDirectory(client, directory)) {
                            log.warn("Can not build directory: " + directory + " (maybe because of denied permissions)");
                        }
                    }
                }

                // try to rename
                boolean success = client.rename(fromName, toName);
                if (!success) {
                    String message = "Can not move file: " + fromName + " to: " + toName;
                    throw new FtpOperationFailedException(client.getReplyCode(), client.getReplyString(), message);
                }
            }

            // all success so lets process it
            getProcessor().process(exchange);
        }
    }

    protected void acquireExclusiveReadLock(FTPClient client, FTPFile ftpFile) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("Waiting for exclusive read lock to file: " + ftpFile);
        }

        // the trick is to try to rename the file, if we can rename then we have exclusive read
        // since its a remote file we can not use java.nio to get a RW lock
        String originalName = ftpFile.getName();
        String newName = originalName + ".camelExclusiveReadLock";
        boolean exclusive = false;
        while (!exclusive) {
            exclusive = client.rename(originalName, newName);
            if (exclusive) {
                if (log.isDebugEnabled()) {
                    log.debug("Acquired exclusive read lock to file: " + originalName);
                }
                // rename it back so we can read it
                client.rename(newName, originalName);
            } else {
                log.trace("Exclusive read lock not granted. Sleeping for 1000 millis.");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    protected String getFileName(Object file) {
        FTPFile ftpFile = (FTPFile) file;
        return ftpFile.getName();
    }

}
