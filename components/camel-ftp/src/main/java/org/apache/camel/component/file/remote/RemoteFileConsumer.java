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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Processor;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for remote file consumers.
 */
public abstract class RemoteFileConsumer extends ScheduledPollConsumer {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected RemoteFileEndpoint endpoint;
    protected RemoteFileOperations operations;
    protected boolean loggedIn;

    public RemoteFileConsumer(RemoteFileEndpoint endpoint, Processor processor, RemoteFileOperations operations) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    protected void poll() throws Exception {
        connectIfNecessary();
        if (!loggedIn) {
            String message = "Could not connect/login to: " + endpoint.remoteServerInformation() + ". Will skip this poll.";
            log.warn(message);
            return;
        }

        // gather list of files to process
        List<RemoteFile> files = new ArrayList<RemoteFile>();

        String name = endpoint.getConfiguration().getFile();
        boolean isDirectory = endpoint.getConfiguration().isDirectory();
        if (isDirectory) {
            pollDirectory(name, endpoint.isRecursive(), files);
        } else {
            pollFile(name, files);
        }

        // sort files using file comparator if provided
        if (endpoint.getSorter() != null) {
            Collections.sort(files, endpoint.getSorter());
        }

        // sort using build in sorters that is expression based
        // first we need to convert to RemoteFileExchange objects so we can sort using expressions
        List<RemoteFileExchange> exchanges = new ArrayList<RemoteFileExchange>(files.size());
        for (RemoteFile file : files) {
            RemoteFileExchange exchange = endpoint.createExchange(file);
            endpoint.configureMessage(file, exchange.getIn());
            exchanges.add(exchange);
        }
        // sort files using exchange comparator if provided
        if (endpoint.getSortBy() != null) {
            Collections.sort(exchanges, endpoint.getSortBy());
        }

        // consume files one by one
        int total = exchanges.size();
        if (total > 0 && log.isDebugEnabled()) {
            log.debug("Total " + total + " files to consume");
        }
        for (int index = 0; index < total; index++) {
            RemoteFileExchange exchange = exchanges.get(index);
            // add current index and total as headers
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_INDEX, index);
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_TOTAL, total);
            processExchange(exchange);
        }
    }

    /**
     * Polls the given directory for files to process
     *
     * @param fileName    current directory or file
     * @param processDir  recursive
     * @param fileList    current list of files gathered
     */
    protected abstract void pollDirectory(String fileName, boolean processDir, List<RemoteFile> fileList);

    /**
     * Polls the given file
     *
     * @param fileName  the file name
     * @param fileList  current list of files gathered
     */
    protected abstract void pollFile(String fileName, List<RemoteFile> fileList);

    /**
     * Processes the exchange
     *
     * @param exchange  the exchange
     */
    protected void processExchange(final RemoteFileExchange exchange) {
        if (log.isTraceEnabled()) {
            log.trace("Processing remote file: " + exchange.getRemoteFile());
        }

        try {
            final RemoteFileProcessStrategy processStrategy = endpoint.getRemoteFileProcessStrategy();

            if (processStrategy.begin(operations, endpoint, exchange, exchange.getRemoteFile())) {

                // must use file from exchange as it can be updated due the preMoveNamePrefix/preMoveNamePostfix options
                final RemoteFile target = exchange.getRemoteFile();
                // must use full name when downloading so we have the correct path
                final String name = target.getAbsolutelFileName();

                // retrieve the file using the stream
                if (log.isTraceEnabled()) {
                    log.trace("Retriving remote file: " + name + " from: " + remoteServer());
                }
                OutputStream os = new ByteArrayOutputStream();
                target.setBody(os);
                operations.retrieveFile(name, os);

                if (log.isTraceEnabled()) {
                    log.trace("Retrieved remote file: " + name + " from: " + remoteServer());
                }

                if (log.isDebugEnabled()) {
                    log.debug("About to process remote file: " + target + " using exchange: " + exchange);
                }
                // Use the async processor interface so that processing of
                // the exchange can happen asynchronously
                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    public void done(boolean sync) {
                        final RemoteFile file = exchange.getRemoteFile();
                        boolean failed = exchange.isFailed();
                        boolean handled = DeadLetterChannel.isFailureHandled(exchange);

                        if (log.isDebugEnabled()) {
                            log.debug("Done processing remote file: " + file.getAbsolutelFileName()
                                + ". Status is: " + (failed ? "failed: " + failed + ", handled by failure processor: " + handled : "processed OK"));
                        }

                        boolean committed = false;
                        try {
                            if (!failed || handled) {
                                // commit the file strategy if there was no failure or already handled by the DeadLetterChannel
                                processStrategyCommit(processStrategy, exchange, file, handled);
                                committed = true;
                            } else {
                                // there was an exception but it was not handled by the DeadLetterChannel
                                handleException(exchange.getException());
                            }
                        } finally {
                            if (!committed) {
                                processStrategyRollback(processStrategy, exchange, file);
                            }
                        }
                    }
                });
            } else {
                log.warn(endpoint + " cannot process remote file: " + exchange.getRemoteFile());
            }
        } catch (Exception e) {
            handleException(e);
        }

    }

    /**
     * Strategy when the file was processed and a commit should be executed.
     *
     * @param remoteFileProcessStrategy the strategy to perform the commit
     * @param exchange                  the exchange
     * @param remoteFile                the file processed
     * @param failureHandled            is <tt>false</tt> if the exchange was processed succesfully, <tt>true</tt> if
     *                                  an exception occured during processing but it was handled by the failure processor (usually the
     *                                  DeadLetterChannel).
     */
    protected void processStrategyCommit(RemoteFileProcessStrategy remoteFileProcessStrategy, RemoteFileExchange exchange,
                                         RemoteFile remoteFile, boolean failureHandled) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Committing remote file strategy: " + remoteFileProcessStrategy + " for file: "
                        + remoteFile + (failureHandled ? " that was handled by the failure processor." : ""));
            }
            remoteFileProcessStrategy.commit(operations, endpoint, exchange, remoteFile);
        } catch (Exception e) {
            log.warn("Error committing remote file strategy: " + remoteFileProcessStrategy, e);
            handleException(e);
        }
    }

    /**
     * Strategy when the file was not processed and a rollback should be executed.
     *
     * @param remoteFileProcessStrategy the strategy to perform the commit
     * @param exchange                  the exchange
     * @param remoteFile                the file processed
     */
    protected void processStrategyRollback(RemoteFileProcessStrategy remoteFileProcessStrategy, RemoteFileExchange exchange,
                                           RemoteFile remoteFile) {
        if (log.isDebugEnabled()) {
            log.debug("Rolling back remote file strategy: " + remoteFileProcessStrategy + " for file: " + remoteFile);
        }
        remoteFileProcessStrategy.rollback(operations, endpoint, exchange, remoteFile);
    }

    /**
     * Strategy for validating if the given remote file should be included or not
     *
     * @param file         the remote file
     * @param isDirectory  wether the file is a directory or a file
     * @return <tt>true</tt> to include the file, <tt>false</tt> to skip it
     */
    protected boolean isValidFile(RemoteFile file, boolean isDirectory) {
        if (!isMatched(file, isDirectory)) {
            if (log.isTraceEnabled()) {
                log.trace("Remote file did not match. Will skip this remote file: " + file);
            }
            return false;
        }

        // file matched
        return true;
    }

    /**
     * Strategy to perform file matching based on endpoint configuration.
     * <p/>
     * Will always return <tt>false</tt> for certain files/folders:
     * <ul>
     *   <li>Starting with a dot</li>
     *   <li>lock files</li>
     * </ul>
     * And then <tt>true</tt> for directories.
     *
     * @param file         the remote file
     * @param isDirectory  wether the file is a directory or a file
     * @return <tt>true</tt> if the remote file is matched, <tt>false</tt> if not
     */
    protected boolean isMatched(RemoteFile file, boolean isDirectory) {
        String name = file.getFileName();

        // folders/names starting with dot is always skipped (eg. ".", ".camel", ".camelLock")
        if (name.startsWith(".")) {
            return false;
        }

        // lock files should be skipped
        if (name.endsWith(FileEndpoint.DEFAULT_LOCK_FILE_POSTFIX)) {
            return false;
        }

        // directories so far is always regarded as matched (matching on the name is only for files)
        if (isDirectory) {
            return true;
        }

        if (endpoint.getFilter() != null) {
            if (!endpoint.getFilter().accept(file)) {
                return false;
            }
        }

        if (ObjectHelper.isNotEmpty(endpoint.getRegexPattern())) {
            if (!name.matches(endpoint.getRegexPattern())) {
                return false;
            }
        }

        if (ObjectHelper.isNotEmpty(endpoint.getExcludedNamePrefix())) {
            if (name.startsWith(endpoint.getExcludedNamePrefix())) {
                return false;
            }
        }
        if (ObjectHelper.isNotEmpty(endpoint.getExcludedNamePostfix())) {
            if (name.endsWith(endpoint.getExcludedNamePostfix())) {
                return false;
            }
        }

        return true;
    }

    protected void doStart() throws Exception {
        log.info("Starting");
        super.doStart();
    }

    protected void doStop() throws Exception {
        log.info("Stopping");
        // disconnect when stopping
        try {
            loggedIn = false;
            log.debug("Disconnecting from " + remoteServer());
            operations.disconnect();
        } catch (RemoteFileOperationFailedException e) {
            // ignore just log a warning
            log.warn(e.getMessage());
        }
        super.doStop();
    }

    protected void connectIfNecessary() throws IOException {
        if (!operations.isConnected() || !loggedIn) {
            if (log.isDebugEnabled()) {
                log.debug("Not connected/logged in, connecting to " + remoteServer());
            }
            loggedIn = operations.connect(endpoint.getConfiguration());
            if (loggedIn) {
                log.info("Connected and logged in to " + remoteServer());
            }
        }
    }

    /**
     * Returns human readable server information for logging purpose
     */
    protected String remoteServer() {
        return endpoint.remoteServerInformation();
    }
}
