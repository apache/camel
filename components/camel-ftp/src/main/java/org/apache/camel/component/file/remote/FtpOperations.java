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
package org.apache.camel.component.file.remote;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.StreamCache;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.file.GenericFileHelper;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.function.ThrowingRunnable;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FTP remote file operations
 */
public class FtpOperations implements RemoteFileOperations<FTPFile> {

    /**
     * How long a disconnect waits at most for the reply to QUIT (soTimeout when it is shorter).
     */
    private static final int QUIT_REPLY_TIMEOUT = 2000;
    /**
     * How long after the server completed an upload, a download or a noop the producer does not send a noop before the
     * next upload, as the connection was just seen working.
     */
    private static final long RECENTLY_USED_NANOS = TimeUnit.SECONDS.toNanos(1);
    /**
     * How many directories autoCreate remembers on a connection at most: the gain is for repeated uploads into the same
     * directories, and a producer that writes into a new directory for each message must not grow without limit.
     */
    private static final int MAX_BUILT_DIRECTORIES = 100;

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final FTPClient client;
    protected final FTPClientConfig clientConfig;
    protected FtpEndpoint<FTPFile> endpoint;
    protected FtpClientActivityListener clientActivityListener;
    /**
     * An FTP connection runs one command at a time, and the operations of a consumer are not only used by the thread
     * that polls: the commit or rollback of a file (delete, move, rename) runs on the thread that completes the
     * exchange, such as a seda consumer when the route sends the file to seda, while the consumer downloads the next
     * file. Each operation holds this lock, so that the commands and replies of two operations do not mix.
     */
    protected final ReentrantLock lock = new ReentrantLock();
    /**
     * The current directory of the connection, as the server gave it to PWD or as an absolute change of directory set
     * it, so that a stepwise operation does not ask for it each time. {@code null} when unknown.
     */
    private String workingDirectory;
    /**
     * The directory to change back to before an operation that uses names relative to the directory where the
     * operations started: a stepwise upload, download or delete stays in the directory of its file, so that the next
     * download or delete in that directory needs no change of directory (an upload changes back and into it again, see
     * {@link #storeFileInItsDirectory(String, Exchange)}). {@code null} when there is nothing to change back.
     */
    private String restoreDirectory;
    /**
     * The directory part of the file name (relative to {@link #restoreDirectory}, or absolute) that a stepwise upload,
     * download or delete changed into. {@code null} when the connection is not in such a directory, also when the
     * change failed half way.
     */
    private String stepwiseDirectory;
    /**
     * Whether an upload, a download or a delete runs, with or without stepwise: the operations that it calls, such as
     * the existence check or the move of an existing file, use names relative to the directory of the file and must not
     * change back.
     */
    private boolean inStepwiseOperation;
    /**
     * The directories that a file was uploaded into on this connection, as absolute paths (see
     * {@link #builtDirectoryKey(String)}), so that autoCreate does not check them again before the next upload. Only an
     * upload adds a directory: the moves of a consumer check their directory each time. Holds the
     * {@link #MAX_BUILT_DIRECTORIES} most recently used ones.
     */
    private final Set<String> builtDirectories = Collections.newSetFromMap(LRUCacheFactory.newLRUCache(MAX_BUILT_DIRECTORIES));
    /**
     * When the server last completed an upload, a download or a noop ({@link System#nanoTime()}), or 0.
     */
    private volatile long lastCompletedNanos;

    private static class TaskPayload {
        final RemoteFileConfiguration configuration;
        private Exception exception;

        public TaskPayload(RemoteFileConfiguration configuration) {
            this.configuration = configuration;
        }
    }

    public FtpOperations(FTPClient client, FTPClientConfig clientConfig) {
        this.client = client;
        this.clientConfig = clientConfig;
    }

    @Override
    public void setEndpoint(GenericFileEndpoint<FTPFile> endpoint) {
        this.endpoint = (FtpEndpoint<FTPFile>) endpoint;
        // setup download listener/logger when we have the endpoint configured
        this.clientActivityListener = new DefaultFtpClientActivityListener(
                this.endpoint, this.endpoint.getConfiguration().remoteServerInformation());
    }

    public FtpClientActivityListener getClientActivityListener() {
        return clientActivityListener;
    }

    public void setClientActivityListener(FtpClientActivityListener clientActivityListener) {
        this.clientActivityListener = clientActivityListener;
    }

    @Override
    public GenericFile<FTPFile> newGenericFile() {
        return new RemoteFile<>();
    }

    @Override
    public boolean connect(RemoteFileConfiguration configuration, Exchange exchange)
            throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // a new connection starts in the login directory
            resetConnectionState();
            client.setCopyStreamListener(clientActivityListener);

            try {
                return doConnect(configuration, exchange);
            } catch (GenericFileOperationFailedException e) {
                clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    protected boolean doConnect(RemoteFileConfiguration configuration, Exchange exchange)
            throws GenericFileOperationFailedException {
        log.trace("Connecting using FTPClient: {}", client);

        String host = configuration.getHost();
        String username = configuration.getUsername();
        String account = ((FtpConfiguration) configuration).getAccount();

        // connecting replaces the socket of the client without closing it: close a connection that is still open, such
        // as one whose server no longer answers, so it is not left open
        if (client.isConnected()) {
            log.trace("Client disconnect before connecting again");
            try {
                client.disconnect();
            } catch (IOException e) {
                log.trace("Ignore exception during disconnect: {}", e.getMessage());
            }
        }

        if (clientConfig != null) {
            log.trace("Configuring FTPClient with config: {}", clientConfig);
            client.configure(clientConfig);
        }

        if (endpoint.getSoTimeout() > 0) {
            client.setDefaultTimeout(endpoint.getSoTimeout());
        }

        if (log.isTraceEnabled()) {
            log.trace("Connecting to {} using connection timeout: {}", configuration.remoteServerInformation(),
                    client.getConnectTimeout());
        }

        ScheduledExecutorService ses = endpoint.getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "FtpReconnect");
        try {
            BlockingTask task = Tasks.backgroundTask()
                    .withBudget(Budgets.iterationTimeBudget()
                            .withMaxIterations(Budgets.atLeastOnce(endpoint.getMaximumReconnectAttempts()))
                            .withUnlimitedDuration()
                            .withInterval(Duration.ofMillis(endpoint.getReconnectDelay()))
                            .build())
                    .withScheduledExecutor(ses)
                    .withName("FtpReconnect")
                    .build();

            TaskPayload payload = new TaskPayload(configuration);

            if (!task.run(endpoint.getCamelContext(), this::tryConnect, payload)) {
                if (exchange != null) {
                    exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
                    exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());
                }

                if (payload.exception != null) {
                    if (payload.exception instanceof GenericFileOperationFailedException genericFileOperationFailedException) {
                        throw genericFileOperationFailedException;
                    } else {
                        // no reply string when the client has no reply, such as after it closed the previous connection
                        throw new GenericFileOperationFailedException(
                                client.getReplyCode(), Objects.toString(client.getReplyString(), ""),
                                payload.exception.getMessage(), payload.exception);
                    }
                } else {
                    throw new GenericFileOperationFailedException(
                            client.getReplyCode(), client.getReplyString(),
                            "Server refused connection");
                }
            }
        } finally {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(ses);
        }

        // we are now connected
        clientActivityListener.onConnected(host);

        // must enter passive mode directly after connect
        if (configuration.isPassiveMode()) {
            log.trace("Using passive mode connections");
            client.enterLocalPassiveMode();
        }

        // must set soTimeout after connect
        if (endpoint.getSoTimeout() > 0) {
            log.trace("Using SoTimeout={}", endpoint.getSoTimeout());
            try {
                client.setSoTimeout(endpoint.getSoTimeout());
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        }

        try {
            clientActivityListener.onLogin(host);
            boolean login;
            if (username != null) {
                if (account != null) {
                    log.trace("Attempting to login user: {} using password: ******** and account: {}", username, account);
                    login = client.login(username, configuration.getPassword(), account);
                } else {
                    log.trace("Attempting to login user: {} using password: ********", username);
                    login = client.login(username, configuration.getPassword());
                }
            } else {
                if (account != null) {
                    // not sure if it makes sense to login anonymous with
                    // account?
                    log.trace("Attempting to login anonymous using account: {}", account);
                    login = client.login("anonymous", "", account);
                } else {
                    log.trace("Attempting to login anonymous");
                    login = client.login("anonymous", "");
                }
            }
            log.trace("User {} logged in: {}", username != null ? username : "anonymous", login);
            if (!login) {
                // store replyString, because disconnect() will reset it
                String replyString = client.getReplyString();
                int replyCode = client.getReplyCode();
                clientActivityListener.onLoginFailed(replyCode, replyString);
                // disconnect to prevent connection leaks
                client.disconnect();
                throw new GenericFileOperationFailedException(replyCode, replyString);
            }
            clientActivityListener.onLoginComplete(host);
            client.setFileType(configuration.isBinary() ? FTP.BINARY_FILE_TYPE : FTP.ASCII_FILE_TYPE);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            if (exchange != null) {
                // store client reply information after the operation
                exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
                exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());
            }
        }

        // site commands
        if (endpoint.getConfiguration().getSiteCommand() != null) {
            // commands can be separated using new line
            Iterator<?> it = ObjectHelper.createIterator(endpoint.getConfiguration().getSiteCommand(), "\n");
            while (it.hasNext()) {
                Object next = it.next();
                String command = endpoint.getCamelContext().getTypeConverter().convertTo(String.class, next);
                log.trace("Site command to send: {}", command);
                if (command != null) {
                    boolean result = sendSiteCommand(command);
                    if (!result) {
                        throw new GenericFileOperationFailedException("Site command: " + command + " returned false");
                    }
                }
            }
        }

        return true;
    }

    private boolean tryConnect(TaskPayload payload) {
        final RemoteFileConfiguration configuration = payload.configuration;
        final String host = configuration.getHost();
        final int port = configuration.getPort();

        try {
            if (log.isTraceEnabled()) {
                log.trace("Reconnect attempt to {}", configuration.remoteServerInformation());
            }

            clientActivityListener.onConnecting(host);
            client.connect(host, port);

            // must check reply code if we are connected
            int reply = client.getReplyCode();

            return FTPReply.isPositiveCompletion(reply);
        } catch (Exception e) {

            payload.exception = e;

            if (client.isConnected()) {
                log.trace("Disconnecting due to exception during connect");
                try {
                    client.disconnect(); // ensures socket is closed
                } catch (IOException ignore) {
                    log.trace("Ignore exception during disconnect: {}", ignore.getMessage());
                }
            }
        }
        return false;
    }

    @Override
    public boolean isConnected() throws GenericFileOperationFailedException {
        return client.isConnected();
    }

    @Override
    public void disconnect() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            resetConnectionState();
            try {
                doDisconnect();
            } catch (GenericFileOperationFailedException e) {
                clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Disconnects without the lock that the operations hold: when the stop interrupts a poll, the poll can still run an
     * operation that the server does not answer, and closing the connection ends it, instead of waiting for it.
     */
    @Override
    public void disconnectOnStop() throws GenericFileOperationFailedException {
        if (isConnected()) {
            try {
                doDisconnect();
            } catch (GenericFileOperationFailedException e) {
                clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Closes the connection without sending QUIT: a forced disconnect recovers from a failure, after which the server
     * may no longer answer, so it must not wait up to soTimeout for the reply.
     */
    @Override
    public void forceDisconnect() throws GenericFileOperationFailedException {
        clientActivityListener.onDisconnecting(endpoint.getConfiguration().remoteServerInformation());
        try {
            log.trace("Client disconnect");
            client.disconnect();
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
        clientActivityListener.onDisconnected(endpoint.getConfiguration().remoteServerInformation());
    }

    protected void doDisconnect() throws GenericFileOperationFailedException {
        // logout before disconnecting
        clientActivityListener.onDisconnecting(endpoint.getConfiguration().remoteServerInformation());
        try {
            log.trace("Client logout");
            // the connection is closed anyway, so a server that no longer answers (it hangs, or the connection was lost
            // without being closed) must not hold the disconnect, such as the stop of the consumer or producer, for
            // soTimeout while waiting for the reply to QUIT
            if (client.isConnected()) {
                int soTimeout = client.getSoTimeout();
                if (soTimeout <= 0 || soTimeout > QUIT_REPLY_TIMEOUT) {
                    client.setSoTimeout(QUIT_REPLY_TIMEOUT);
                }
            }
            client.logout();
            client.disconnect();
        } catch (IOException e) {
            GenericFileOperationFailedException gfo = new GenericFileOperationFailedException(
                    client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            try {
                log.trace("Client disconnect");
                client.disconnect();
            } catch (IOException ed) {
                log.warn("Failed to disconnect: {}", e.getMessage(), e);
                gfo.addSuppressed(ed);
            }

            throw gfo;
        }

        clientActivityListener.onDisconnected(endpoint.getConfiguration().remoteServerInformation());
    }

    @Override
    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.debug("Deleting file: {}", name);
            checkName(name);

            boolean result;
            boolean outer = inStepwiseOperation;
            try {
                reconnectIfNecessary(null);
                if (outer) {
                    result = deleteFileDuringOperation(name);
                } else {
                    inStepwiseOperation = true;
                    String target = changeToDirectoryOf(name);

                    // delete the file
                    log.trace("Client deleteFile: {}", target);
                    result = client.deleteFile(target);
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            } finally {
                inStepwiseOperation = outer;
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deletes a file while an upload or a download runs, such as from a custom move-existing strategy of the upload:
     * the name is relative to the directory of the file of that operation, so a stepwise delete changes in relative to
     * that directory and back, and leaves the connection where the operation needs it.
     */
    private boolean deleteFileDuringOperation(String name) throws IOException {
        String path = FileUtil.onlyPath(name);
        if (path == null || !endpoint.getConfiguration().isStepwise()) {
            log.trace("Client deleteFile: {}", name);
            return client.deleteFile(name);
        }
        String current = currentDirectory();
        try {
            workingDirectory = null;
            changeDirectory(FtpUtils.compactPath(path));
            String target = FileUtil.stripPath(name);
            log.trace("Client deleteFile: {}", target);
            return client.deleteFile(target);
        } finally {
            changeDirectory(current);
            workingDirectory = current;
        }
    }

    @Override
    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.debug("Renaming file: {} to: {}", from, to);
            checkName(from);
            checkName(to);
            try {
                reconnectIfNecessary(null);
                leaveStepwiseDirectory();
                return client.rename(from, to);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // must normalize directory first
            directory = endpoint.getConfiguration().normalizePath(directory);
            checkName(directory);

            log.trace("buildDirectory({})", directory);
            String key = builtDirectoryKey(directory);
            if (builtDirectories.contains(key)) {
                log.trace("Directory {} was checked or created before on this connection", directory);
                return true;
            }
            leaveStepwiseDirectory();
            try {
                String originalDirectory = getCurrentDirectory();

                boolean success;
                // maybe the full directory already exists
                workingDirectory = null;
                success = client.changeWorkingDirectory(directory);
                if (!success) {
                    log.trace("Trying to build remote directory: {}", directory);
                    success = client.makeDirectory(directory);
                    if (!success) {
                        // we are here if the server side doesn't create
                        // intermediate folders so create the folder one by one
                        success = buildDirectoryChunks(directory);
                    }
                }

                // change back to original directory
                if (originalDirectory != null) {
                    changeCurrentDirectory(originalDirectory);
                }

                return success;
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean retrieveFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            checkName(name);
            // store the name of the file to download on the listener
            clientActivityListener.setDownload(true);
            clientActivityListener.setRemoteFileName(name);
            clientActivityListener.setRemoteFileSize(size);
            clientActivityListener.onBeginDownloading(endpoint.getConfiguration().remoteServerInformation(), name);

            boolean answer;
            boolean outer = inStepwiseOperation;
            inStepwiseOperation = true;
            try {
                log.trace("retrieveFile({})", name);
                if (org.apache.camel.util.ObjectHelper.isNotEmpty(endpoint.getLocalWorkDirectory())) {
                    // local work directory is configured so we should store file
                    // content as files in this local directory
                    answer = retrieveFileToFileInLocalWorkDirectory(name, exchange, endpoint.isResumeDownload());
                } else {
                    // store file content directory as stream on the body
                    answer = retrieveFileToStreamInBody(name, exchange);
                }
            } catch (GenericFileOperationFailedException e) {
                clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
                throw e;
            } finally {
                inStepwiseOperation = outer;
            }

            if (answer) {
                lastCompletedNanos = System.nanoTime();
                clientActivityListener.onDownloadComplete(endpoint.getConfiguration().remoteServerInformation(), name);
            }
            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void releaseRetrievedFileResources(Exchange exchange) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            InputStream is = exchange.getIn().getHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, InputStream.class);

            if (is != null) {
                try {
                    IOHelper.close(is);
                    boolean ok = client.completePendingCommand();
                    if (!ok) {
                        throw new GenericFileOperationFailedException(
                                client.getReplyCode(), client.getReplyString());
                    }
                } catch (SocketTimeoutException e) {
                    // the server did not answer in time at the end of the streamed transfer: a reply that comes late would be
                    // read as the reply to the next command, so the connection cannot be used any more. It is closed
                    // without waiting on it, and the consumer leaves the rest of the batch to the next poll, which connects
                    // again
                    GenericFileOperationFailedException failure = new GenericFileOperationFailedException(e.getMessage(), e);
                    try {
                        forceDisconnect();
                    } catch (GenericFileOperationFailedException fe) {
                        failure.addSuppressed(fe);
                    }
                    throw failure;
                } catch (IOException e) {
                    throw new GenericFileOperationFailedException(e.getMessage(), e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToStreamInBody(String name, Exchange exchange) throws GenericFileOperationFailedException {
        if (endpoint.getConfiguration().isStepwise() && endpoint.getConfiguration().isStreamDownload()) {
            //stepwise and streamDownload are not supported together
            throw new IllegalArgumentException("The option stepwise is not supported for stream downloading");
        }
        boolean result;
        try {
            GenericFile<FTPFile> target = (GenericFile<FTPFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            org.apache.camel.util.ObjectHelper.notNull(target,
                    "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");

            String remoteName = changeToDirectoryOf(name);

            log.trace("Client retrieveFile: {}", remoteName);
            if (endpoint.getConfiguration().isStreamDownload()) {
                InputStream is = client.retrieveFileStream(remoteName);
                if (is != null) {
                    target.setBody(is);
                    exchange.getIn().setHeader(FtpConstants.REMOTE_FILE_INPUT_STREAM, is);
                }
                result = is != null;
            } else {
                // read the entire file into memory in the byte array
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                result = client.retrieveFile(remoteName, bos);
                // close the stream after done
                IOHelper.close(bos);

                target.setBody(bos.toByteArray());
            }

            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean retrieveFileToFileInLocalWorkDirectory(String name, Exchange exchange, boolean resumeDownload)
            throws GenericFileOperationFailedException {
        File temp;
        File local = new File(FileUtil.normalizePath(endpoint.getLocalWorkDirectory()));
        OutputStream os;
        long existingSize = -1;

        try {
            // use relative filename in local work directory
            GenericFile<FTPFile> target = (GenericFile<FTPFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            org.apache.camel.util.ObjectHelper.notNull(target,
                    "Exchange should have the " + FileComponent.FILE_EXCHANGE_FILE + " set");
            String relativeName = target.getRelativeFilePath();

            File localWorkDir = local;
            temp = new File(local, relativeName + ".inprogress");
            local = new File(local, relativeName);

            // ensure the local work file stays within the local work directory (CAMEL-23765)
            if (endpoint.isJailStartingDirectory()) {
                GenericFileHelper.jailToLocalWorkDirectory(temp, localWorkDir);
                GenericFileHelper.jailToLocalWorkDirectory(local, localWorkDir);
            }

            // create directory to local work file
            boolean result = local.mkdirs();
            if (!result) {
                log.warn(
                        "Failed to create local directory {} while retrieving file in local work directory. Directory may already exist or have been created externally",
                        local);
            }

            // delete any local file (as its the temp file that is in the
            // in-progress download)
            if (local.exists()) {
                if (!FileUtil.deleteFile(local)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + local);
                }
            }

            // if a previous file exists then store its current size as its a
            // partial download
            boolean exists = temp.exists();
            if (exists) {
                existingSize = temp.length();
            }

            // if we do not resume download, then delete any existing temp file
            // and create a new to use for in-progress download
            if (!resumeDownload) {
                // delete any existing files
                if (exists && !FileUtil.deleteFile(temp)) {
                    throw new GenericFileOperationFailedException("Cannot delete existing local work file: " + temp);
                }
                // create new temp local work file
                if (!temp.createNewFile()) {
                    throw new GenericFileOperationFailedException("Cannot create new local work file: " + temp);
                }
            }

            // store content as a file in the local work directory in the temp
            // handle
            boolean append = resumeDownload && existingSize > 0;
            os = new FileOutputStream(temp, append);

            // set header with the path to the local work file
            exchange.getIn().setHeader(FtpConstants.FILE_LOCAL_WORK_PATH, local.getPath());

        } catch (Exception e) {
            throw new GenericFileOperationFailedException("Cannot create new local work file: " + local, e);
        }

        boolean result;
        try {
            GenericFile<FTPFile> target = (GenericFile<FTPFile>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            // store the java.io.File handle as the body
            target.setBody(local);

            String remoteName = changeToDirectoryOf(name);

            // the file exists so lets try to resume the download
            if (resumeDownload && existingSize > 0) {
                clientActivityListener.onResumeDownloading(endpoint.getConfiguration().remoteServerInformation(), name,
                        existingSize);
                log.trace("Client restartOffset: {}", existingSize);
                log.debug("Resuming download of file: {} at position: {}", remoteName, existingSize);
                client.setRestartOffset(existingSize);
            }
            log.trace("Client retrieveFile: {}", remoteName);
            result = client.retrieveFile(remoteName, os);

            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());

        } catch (IOException e) {
            log.trace("Error occurred during retrieving file: {} to local directory.", name);
            // if we do not attempt to resume download, then attempt to delete
            // the temporary file
            if (!resumeDownload) {
                log.trace("Deleting local work file: {}", name);
                // failed to retrieve the file so we need to close streams and
                // delete in progress file
                // must close stream before deleting file
                IOHelper.close(os, "retrieve: " + name, log);
                boolean deleted = FileUtil.deleteFile(temp);
                if (!deleted) {
                    log.warn("Error occurred during retrieving file: {} to local directory. Cannot delete local work file: {}",
                            temp, name);
                }
            }
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } finally {
            // need to close the stream before rename it
            IOHelper.close(os, "retrieve: " + name, log);
        }

        log.debug("Retrieve file to local work file result: {}", result);

        if (result) {
            log.trace("Renaming local in progress file from: {} to: {}", temp, local);
            // operation went okay so rename temp to local after we have
            // retrieved the data
            try {
                if (!FileUtil.renameFile(temp, local, false)) {
                    throw new GenericFileOperationFailedException(
                            "Cannot rename local work file from: " + temp + " to: " + local);
                }
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        "Cannot rename local work file from: " + temp + " to: " + local, e);
            }
        }

        return result;
    }

    @Override
    public boolean storeFile(String name, Exchange exchange, long size) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            // must normalize name first
            name = endpoint.getConfiguration().normalizePath(name);
            // before the listener and the client, so nothing is sent for a name that is refused
            checkName(name);

            // store the name of the file to upload on the listener
            clientActivityListener.setDownload(false);
            clientActivityListener.setRemoteFileName(name);
            clientActivityListener.setRemoteFileSize(size);
            clientActivityListener.onBeginUploading(endpoint.getConfiguration().remoteServerInformation(), name);

            log.trace("storeFile({})", name);

            boolean answer;
            try {
                GenericFileOperationFailedException failure = null;
                try {
                    answer = storeFileInItsDirectory(name, exchange);
                } catch (GenericFileOperationFailedException e) {
                    failure = e;
                    answer = false;
                }
                if (!answer) {
                    // the reply to the change of directory or to STOR, not to a SITE CHMOD that may follow it
                    int code = failure != null ? failure.getCode() : storeReplyCode(exchange);
                    if (FTPReply.isNegativePermanent(code) && isBuiltDirectoryMissing(name, exchange, failure)) {
                        answer = storeFileInBuiltDirectoryAgain(name, exchange, failure);
                    } else if (failure != null) {
                        throw failure;
                    }
                }
                if (answer) {
                    rememberBuiltDirectory(FileUtil.onlyPath(name));
                }
            } catch (GenericFileOperationFailedException e) {
                clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
                throw e;
            }

            if (answer) {
                lastCompletedNanos = System.nanoTime();
                clientActivityListener.onUploadComplete(endpoint.getConfiguration().remoteServerInformation(), name);
            }

            if (!answer) {
                Integer code = exchange.getIn().getHeader(FtpConstants.FTP_REPLY_CODE, Integer.class);
                String status = exchange.getIn().getHeader(FtpConstants.FTP_REPLY_STRING, String.class);
                if (code != null && status != null) {
                    throw new GenericFileOperationFailedException(
                            code, status, "Error writing file [" + targetNameOf(name) + "]");
                }
            }

            return answer;
        } finally {
            lock.unlock();
        }
    }

    private boolean doStoreFile(String name, String targetName, Exchange exchange) throws GenericFileOperationFailedException {
        log.trace("doStoreFile({})", targetName);

        boolean existFile = false;
        // if an existing file already exists what should we do?
        if (endpoint.getFileExist() == GenericFileExist.Ignore || endpoint.getFileExist() == GenericFileExist.Fail
                || endpoint.getFileExist() == GenericFileExist.Move || endpoint.getFileExist() == GenericFileExist.Append) {
            existFile = existsFile(targetName);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                log.trace("An existing file already exists: {}. Ignore and do not override it.", name);
                return true;
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + name + ". Cannot write new file.");
            } else if (existFile && endpoint.getFileExist() == GenericFileExist.Move) {
                // move any existing file first
                this.endpoint.getMoveExistingFileStrategy().moveExistingFile(endpoint, this, targetName);
            }
        }

        InputStream is = null;
        if (exchange.getIn().getBody() == null) {
            // Do an explicit test for a null body and decide what to do
            if (endpoint.isAllowNullBody()) {
                log.trace("Writing empty file.");
                is = new ByteArrayInputStream(new byte[] {});
            } else {
                throw new GenericFileOperationFailedException("Cannot write null body to file: " + name);
            }
        }

        try {
            if (is == null) {
                String charset = endpoint.getCharset();
                if (charset != null) {
                    // charset configured so we must convert to the desired
                    // charset so we can write with encoding
                    is = GenericFileHelper.toInputStream(exchange, charset);
                    log.trace("Using InputStream {} with charset {}.", is, charset);
                } else {
                    is = GenericFileHelper.mandatoryBodyStream(exchange, exchange.getIn(), FileUtil.stripPath(name));
                }
            }

            final StopWatch watch = new StopWatch();
            boolean answer;
            log.debug("About to store file: {} using stream: {}", targetName, is);
            if (existFile && endpoint.getFileExist() == GenericFileExist.Append) {
                log.trace("Client appendFile: {}", targetName);
                answer = client.appendFile(targetName, is);
            } else {
                log.trace("Client storeFile: {}", targetName);
                answer = client.storeFile(targetName, is);
            }
            if (log.isDebugEnabled()) {
                long time = watch.taken();
                log.debug("Took {} ({} millis) to store file: {} and FTP client returned: {}",
                        TimeUtils.printDuration(time, true), time, targetName, answer);
            }

            // store client reply information after the operation
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_CODE, client.getReplyCode());
            exchange.getIn().setHeader(FtpConstants.FTP_REPLY_STRING, client.getReplyString());

            // after storing file, we may set chmod on the file
            String chmod = endpoint.getConfiguration().getChmod();
            if (org.apache.camel.util.ObjectHelper.isNotEmpty(chmod)) {
                log.debug("Setting chmod: {} on file: {}", chmod, targetName);
                String command = "chmod " + chmod + " " + targetName;
                log.trace("Client sendSiteCommand: {}", command);
                boolean success = client.sendSiteCommand(command);
                log.trace("Client sendSiteCommand successful: {}", success);
            }

            return answer;

        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        } catch (InvalidPayloadException e) {
            // say why: the body cannot be written as it is (CAMEL-27151)
            throw new GenericFileOperationFailedException("Cannot store file: " + name + ": " + e.getMessage(), e);
        } finally {
            IOHelper.close(is, "store: " + name, log);
        }
    }

    private boolean storeFileInItsDirectory(String name, Exchange exchange) {
        // change into the directory of the file for each upload, even when the previous upload left the connection
        // there: a server such as vsftpd keeps the directory itself, not its path, so after the directory was renamed
        // or replaced, a STOR without the change would store the file in the old directory, without an error
        leaveStepwiseDirectory();
        boolean outer = inStepwiseOperation;
        inStepwiseOperation = true;
        try {
            return doStoreFile(name, changeToDirectoryOf(name), exchange);
        } finally {
            inStepwiseOperation = outer;
        }
    }

    private boolean storeFileInBuiltDirectoryAgain(
            String name, Exchange exchange, GenericFileOperationFailedException failure) {
        String directory = FileUtil.onlyPath(name);
        String reason = failure != null
                ? failure.getMessage() : exchange.getIn().getHeader(FtpConstants.FTP_REPLY_STRING, String.class);
        log.info("Cannot store file: {} as directory: {}, where a file was stored before on this connection, was removed"
                 + " since ({}). Creating the directory again and storing the file once more.",
                name, directory, reason);
        try {
            if (!buildDirectory(directory, false)) {
                throw new GenericFileOperationFailedException(
                        "Cannot store file: " + name + " as directory: " + directory
                                                              + " was removed and cannot be created again");
            }
            if (exchange.getIn().getBody() instanceof StreamCache cache) {
                // the failed attempt may have read it
                cache.reset();
            }
            return storeFileInItsDirectory(name, exchange);
        } catch (GenericFileOperationFailedException retryFailure) {
            if (failure != null) {
                retryFailure.addSuppressed(failure);
            }
            throw retryFailure;
        }
    }

    private int storeReplyCode(Exchange exchange) {
        Integer code = exchange.getIn().getHeader(FtpConstants.FTP_REPLY_CODE, Integer.class);
        return code != null ? code : client.getReplyCode();
    }

    /**
     * Records that a file was stored in a directory, so that autoCreate does not check it again on this connection.
     */
    private void rememberBuiltDirectory(String directory) {
        if (directory != null) {
            builtDirectories.add(builtDirectoryKey(endpoint.getConfiguration().normalizePath(directory)));
        }
    }

    /**
     * As {@link #isBuiltDirectoryMissing(String, Exchange)}, but a failure of the check is added to the failure of the
     * upload, which is thrown, so that the reason of the failed upload is kept.
     */
    private boolean isBuiltDirectoryMissing(String name, Exchange exchange, GenericFileOperationFailedException failure) {
        try {
            return isBuiltDirectoryMissing(name, exchange);
        } catch (RuntimeException checkFailure) {
            if (failure == null) {
                throw checkFailure;
            }
            failure.addSuppressed(checkFailure);
            throw failure;
        }
    }

    /**
     * Whether an upload failed as the directory where a file was stored before on this connection was removed since,
     * and can be tried again once the directory is created again: autoCreate is enabled, the body can be read again,
     * and the upload does not append (it could append twice). Changes back to the directory where the operations
     * started, forgets the directory so that the next upload checks it, and sends a CWD to check that the directory is
     * missing, as the server gives no specific reply for it.
     */
    private boolean isBuiltDirectoryMissing(String name, Exchange exchange) {
        String directory = FileUtil.onlyPath(name);
        if (directory == null || !endpoint.isAutoCreate()) {
            return false;
        }
        changeBackToRestoreDirectory();
        directory = endpoint.getConfiguration().normalizePath(directory);
        boolean known = builtDirectories.remove(builtDirectoryKey(directory));
        if (!known || !FtpUtils.isBodyReadableAgain(exchange.getIn().getBody())
                || endpoint.getFileExist() == GenericFileExist.Append) {
            return false;
        }
        // the server gives no specific reply for a missing directory, so check that it is missing
        String original = currentDirectory();
        try {
            workingDirectory = null;
            if (client.changeWorkingDirectory(directory)) {
                changeCurrentDirectory(original);
                return false;
            }
            workingDirectory = original;
            return true;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(
                    client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    private String targetNameOf(String name) {
        return endpoint.getConfiguration().isStepwise() ? FileUtil.stripPath(name) : name;
    }

    /**
     * Changes into the directory of a file for a stepwise upload, download or delete, as some servers cannot use a path
     * in the name of the file, unless the connection is already there, and returns the name relative to it. Without
     * stepwise, or for a file without a path, changes back to the directory where the operations started and returns
     * the name.
     */
    private String changeToDirectoryOf(String name) {
        String path = FileUtil.onlyPath(name);
        if (path == null || !endpoint.getConfiguration().isStepwise()) {
            changeBackToRestoreDirectory();
            return name;
        }
        changeToStepwiseDirectory(path);
        return FileUtil.stripPath(name);
    }

    /**
     * Changes into the directory of a stepwise operation, unless a previous one left the connection there, and
     * remembers the directory to change back to. When the change fails half way, the next operation changes back.
     */
    private void changeToStepwiseDirectory(String path) {
        if (restoreDirectory != null && path.equals(stepwiseDirectory)) {
            log.trace("Already in directory: {}", path);
            return;
        }
        changeBackToRestoreDirectory();
        restoreDirectory = currentDirectory();
        changeCurrentDirectory(path);
        stepwiseDirectory = path;
    }

    /**
     * Changes back to the directory where a stepwise operation started, before an operation that uses names relative to
     * it. Does nothing while a stepwise operation runs, as the operations it calls use names relative to the directory
     * of its file.
     */
    private void leaveStepwiseDirectory() {
        if (!inStepwiseOperation) {
            changeBackToRestoreDirectory();
        }
    }

    private void changeBackToRestoreDirectory() {
        String directory = restoreDirectory;
        if (directory == null) {
            return;
        }
        restoreDirectory = null;
        stepwiseDirectory = null;
        try {
            changeCurrentDirectory(directory);
        } catch (RuntimeException e) {
            // the next operation tries again
            restoreDirectory = directory;
            throw e;
        }
    }

    private void resetConnectionState() {
        workingDirectory = null;
        restoreDirectory = null;
        stepwiseDirectory = null;
        builtDirectories.clear();
        lastCompletedNanos = 0;
    }

    /**
     * The key of a directory that autoCreate checked or created: a relative directory is relative to the directory that
     * a relative name resolves against, once the connection changed back from a stepwise operation.
     */
    private String builtDirectoryKey(String directory) {
        if (FileUtil.hasLeadingSeparator(directory)) {
            return directory;
        }
        String base = !inStepwiseOperation && restoreDirectory != null ? restoreDirectory : currentDirectory();
        return base + "/" + directory;
    }

    private String currentDirectory() {
        if (workingDirectory == null) {
            try {
                workingDirectory = client.printWorkingDirectory();
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        }
        return workingDirectory;
    }

    @Override
    public boolean storeFileDirectly(String name, String payload) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            checkName(name);
            leaveStepwiseDirectory();
            ByteArrayInputStream bis = new ByteArrayInputStream(payload.getBytes());
            try {
                return client.storeFile(name, bis);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            } finally {
                IOHelper.close(bis);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.trace("existsFile({})", name);
            checkName(name);
            leaveStepwiseDirectory();
            if (endpoint.isFastExistsCheck()) {
                return fastExistsFile(name);
            }
            // check whether a file already exists
            String directory = FileUtil.onlyPath(name);
            String onlyName = FileUtil.stripPath(name);
            try {
                String[] names;
                if (directory != null) {
                    names = client.listNames(directory);
                } else {
                    names = client.listNames();
                }
                // can return either null or an empty list depending on FTP servers
                if (names == null) {
                    return false;
                }
                for (String existing : names) {
                    log.trace("Existing file: {}, target file: {}", existing, name);
                    existing = FileUtil.stripPath(existing);
                    if (existing != null && existing.equals(onlyName)) {
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    protected boolean fastExistsFile(String name) throws GenericFileOperationFailedException {
        log.trace("fastExistsFile({})", name);
        checkName(name);
        try {
            String[] names = client.listNames(name);
            if (names == null) {
                return false;
            }
            return names.length >= 1;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
    }

    @Override
    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.trace("getCurrentDirectory()");
            leaveStepwiseDirectory();
            String answer = currentDirectory();
            log.trace("Current dir: {}", answer);
            return answer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.trace("changeCurrentDirectory({})", path);
            if (org.apache.camel.util.ObjectHelper.isEmpty(path)) {
                return;
            }
            checkName(path);
            leaveStepwiseDirectory();

            // must compact path so FTP server can traverse correctly
            // use the ftp utils implementation of the compact path
            path = FtpUtils.compactPath(path);

            workingDirectory = null;
            changeDirectory(path);
            if (FileUtil.hasLeadingSeparator(path)) {
                // the server is now in that directory, so a stepwise operation does not ask for it
                workingDirectory = path;
            }
        } finally {
            lock.unlock();
        }
    }

    private void changeDirectory(String path) {
        // not stepwise should change directory in one operation
        if (!endpoint.getConfiguration().isStepwise()) {
            doChangeDirectory(path);
            return;
        }

        // if it starts with the root path then a little special handling for
        // that
        if (FileUtil.hasLeadingSeparator(path)) {
            // change to root path
            doChangeDirectory(path.substring(0, 1));
            path = path.substring(1);
        }

        // split into multiple dirs
        final String[] dirs = path.split("/|\\\\");

        if (dirs == null || dirs.length == 0) {
            // path was just a relative single path
            doChangeDirectory(path);
            return;
        }

        // there are multiple dirs so do this in chunks
        for (String dir : dirs) {
            doChangeDirectory(dir);
        }
    }

    private void doChangeDirectory(String path) {
        if (path == null || ".".equals(path) || org.apache.camel.util.ObjectHelper.isEmpty(path)) {
            return;
        }

        log.trace("Changing directory: {}", path);
        boolean success;
        try {
            if ("..".equals(path)) {
                changeToParentDirectory();
                success = true;
            } else {
                success = client.changeWorkingDirectory(path);
            }
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
        }
        if (!success) {
            // the reply to the change of directory, before the command that reads the current directory replaces it
            int replyCode = client.getReplyCode();
            String replyString = client.getReplyString();
            String message = "Cannot change directory to: " + path;
            if (!FileUtil.hasLeadingSeparator(path)) {
                // give the directory that a relative path starts from (the login directory, for the directory of the
                // endpoint)
                try {
                    String current = client.printWorkingDirectory();
                    if (current != null) {
                        message += ". The path is relative to the current directory: " + current;
                    }
                } catch (IOException e) {
                    log.trace("Cannot get the current directory: {}", e.getMessage(), e);
                }
            }
            throw new GenericFileOperationFailedException(replyCode, replyString, message);
        }
    }

    @Override
    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            leaveStepwiseDirectory();
            workingDirectory = null;
            try {
                client.changeToParentDirectory();
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public FTPFile[] listFiles() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.trace("Listing remote files");
            leaveStepwiseDirectory();
            clientActivityListener.onScanningForFiles(endpoint.remoteServerInformation(), null);
            try {
                return checkListing(client.listFiles(), "the current directory");
            } catch (IOException e) {
                clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public FTPFile[] listFiles(String path) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.trace("Listing remote files from path {}", path);
            clientActivityListener.onScanningForFiles(endpoint.remoteServerInformation(), path);

            // use current directory if path not given
            if (org.apache.camel.util.ObjectHelper.isEmpty(path)) {
                path = ".";
            }
            checkName(path);
            leaveStepwiseDirectory();

            try {
                return checkListing(client.listFiles(path), "directory: " + path);
            } catch (IOException e) {
                clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), e.getMessage());
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * The FTP client returns an empty listing, without an exception, when the server refuses the data connection (PORT,
     * EPRT, PASV, EPSV) or the LIST command, so an empty listing fails unless the server completed it. The replies 450
     * and 550 still give an empty listing, as servers use them for a directory that is missing or empty.
     */
    private FTPFile[] checkListing(FTPFile[] files, String what) {
        if (files != null && files.length > 0) {
            return files;
        }
        int code = client.getReplyCode();
        if (FTPReply.isPositiveCompletion(code) || code == FTPReply.FILE_ACTION_NOT_TAKEN
                || code == FTPReply.FILE_UNAVAILABLE) {
            return files;
        }
        String message = "Cannot list " + what;
        if (!endpoint.getConfiguration().isPassiveMode()) {
            message += ". Set passiveMode=true if the server or a firewall refuses data connections in active mode";
        }
        clientActivityListener.onGeneralError(endpoint.getConfiguration().remoteServerInformation(), message);
        throw new GenericFileOperationFailedException(code, client.getReplyString(), message);
    }

    @Override
    public boolean sendNoop() throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.trace("sendNoOp");
            try {
                boolean answer = client.sendNoOp();
                if (answer) {
                    lastCompletedNanos = System.nanoTime();
                }
                return answer;
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean sendSiteCommand(String command) throws GenericFileOperationFailedException {
        lock.lock();
        try {
            log.trace("sendSiteCommand({})", command);
            leaveStepwiseDirectory();
            try {
                return client.sendSiteCommand(command);
            } catch (IOException e) {
                throw new GenericFileOperationFailedException(
                        client.getReplyCode(), client.getReplyString(), e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * The FTP control connection sends one command per line, so a file or directory name with a carriage return or a
     * line feed cannot be sent as the argument of a single command. Such a name is refused before any command is sent.
     * The message gives the character and its index, not the name.
     */
    private static void checkName(String name) {
        if (name == null) {
            return;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '\r' || ch == '\n') {
                throw new GenericFileOperationFailedException(
                        String.format("Cannot use the file or directory name: it has the character U+%04X at index %d, and a"
                                      + " carriage return or a line feed cannot be sent in an FTP command",
                                (int) ch, i));
            }
        }
    }

    protected FTPClient getFtpClient() {
        return client;
    }

    private boolean buildDirectoryChunks(String dirName) throws IOException {
        final StringBuilder sb = new StringBuilder(dirName.length());
        final String[] dirs = dirName.split("/|\\\\");

        boolean success = false;
        for (String dir : dirs) {
            sb.append(dir).append('/');
            // must normalize the directory name
            String directory = endpoint.getConfiguration().normalizePath(sb.toString());

            // do not try to build root folder (/ or \)
            if (!(directory.equals("/") || directory.equals("\\"))) {
                log.trace("Trying to build remote directory by chunk: {}", directory);

                // while creating directory string if directory results in
                // trailing slash, remove it not necessary
                directory = FileUtil.stripTrailingSeparator(directory);

                success = client.makeDirectory(directory);
            }
        }

        return success;
    }

    /**
     * The FTP client, in the directory where the operations started: a stepwise upload, download or delete stays in the
     * directory of its file until another operation needs the starting directory, so this changes back first. The next
     * upload, download or delete may change directory again. As the caller can change directory with the client, the
     * current directory is asked for again when an operation needs it.
     */
    public FTPClient getClient() {
        lock.lock();
        try {
            leaveStepwiseDirectory();
            workingDirectory = null;
            return client;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Runs the check, such as the noop of the producer health check, unless another thread holds the lock, such as an
     * upload, which holds it for the whole transfer. The check runs with the lock held, so an upload that starts
     * meanwhile waits for it.
     */
    @Override
    public boolean runIfIdle(ThrowingRunnable<Exception> check) throws Exception {
        if (!lock.tryLock()) {
            return false;
        }
        try {
            check.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isRecentlyUsed() {
        long last = lastCompletedNanos;
        return last != 0 && System.nanoTime() - last < RECENTLY_USED_NANOS;
    }

    private void reconnectIfNecessary(Exchange exchange) throws GenericFileOperationFailedException {
        boolean reconnectRequired;
        try {
            boolean connected = isConnected();
            if (connected && !sendNoop()) {
                reconnectRequired = true;
            } else {
                reconnectRequired = !connected;
            }
        } catch (GenericFileOperationFailedException e) {
            // Ignore Exception and reconnect the client
            reconnectRequired = true;
        }
        if (reconnectRequired) {
            log.trace("Client is not connected anymore, try to reconnect");
            connect(endpoint.getConfiguration(), exchange);
        }
    }

}
